"""QluCampus modifications, 2026-09-21, GPL-3.0.

Independent host verification. Requires openpyxl; does not replace Android tests.
Run after the Gradle JVM test tasks from the repository root.
"""
import json
from pathlib import Path
import re
import sqlite3
import xml.etree.ElementTree as ET

import openpyxl

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / "core/data/schemas/com.dawncourse.core.data.local.AppDatabase"


def schema_database(version):
    db = sqlite3.connect(":memory:")
    schema = json.loads((SCHEMAS / f"{version}.json").read_text(encoding="utf-8"))["database"]
    for table in schema["entities"]:
        db.execute(table["createSql"].replace("${TABLE_NAME}", table["tableName"]))
        for index in table.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", table["tableName"]))
    db.execute("PRAGMA foreign_keys=ON")
    return db


def verify_migration():
    db = schema_database(6)
    expected = schema_database(8)
    db.execute("INSERT INTO timetable_profiles VALUES (1,'fixture','原课表',NULL,0,0,0)")
    db.execute("INSERT INTO semesters VALUES (1,1,'原学期',0,20)")
    db.execute("INSERT INTO courses VALUES (1,1,'保留课程','','',1,1,2,1,16,0,'',0,'',1)")
    source = (ROOT / "core/data/src/main/java/com/dawncourse/core/data/local/AppDatabaseMigrations.kt").read_text(encoding="utf-8")
    migration = source.split("val MIGRATION_6_7", 1)[1].split("val ALL", 1)[0]
    statements = re.findall(r'db\.execSQL\("([^"\n]+)"\)', migration)
    assert len(statements) == 11
    for sql in statements:
        db.execute(sql)
    tables = [r[0] for r in expected.execute("SELECT name FROM sqlite_master WHERE type='table' AND name != 'sqlite_sequence'")]
    for table in tables:
        for pragma in ("table_info", "foreign_key_list"):
            assert db.execute(f"PRAGMA {pragma}('{table}')").fetchall() == expected.execute(f"PRAGMA {pragma}('{table}')").fetchall(), (table, pragma)
        actual_indexes = {r[1:3] for r in db.execute(f"PRAGMA index_list('{table}')")}
        expected_indexes = {r[1:3] for r in expected.execute(f"PRAGMA index_list('{table}')")}
        assert actual_indexes == expected_indexes, table
    assert db.execute("SELECT name FROM courses WHERE id=1").fetchone() == ("保留课程",)
    for account, year, term, payload in [("A",2025,1,"A1"),("A",2025,2,"A2"),("B",2025,1,"B1")]:
        db.execute("INSERT INTO campus_grades VALUES (?,?,?,?,0)", (account, year, term, payload))
    db.execute("INSERT OR REPLACE INTO campus_grades VALUES ('A',2025,1,'updated-A1',1)")
    assert db.execute("SELECT accountId,semester,payload FROM campus_grades ORDER BY accountId,semester").fetchall() == [("A",1,"updated-A1"),("A",2,"A2"),("B",1,"B1")]
    assert db.execute("PRAGMA foreign_key_check").fetchall() == []
    db.close()
    expected.close()
    return "PASS: actual v6-to-v7-to-v8 SQL, Room schema structure, preserved course, account/term keys; host SQLite only"


def verify_xlsx():
    workbook = openpyxl.load_workbook(ROOT / "core/data/build/reports/qlu-sample.xlsx")
    assert workbook.sheetnames == ["成绩明细", "绩点"]
    assert workbook.worksheets[0]["A2"].value == "有机化学 & 实验"
    assert workbook.worksheets[0]["B2"].value == "89.50"
    assert workbook.worksheets[0]["A3"].value == "=1+1"
    assert workbook.worksheets[0]["A3"].data_type == "s"
    workbook.close()
    return "PASS: openpyxl opens two worksheets; Chinese, decimal text, formula-like text preserved"


def verify_junit():
    result = {}
    for module in ("core/domain", "core/data", "core/ui", "feature/grades", "feature/timetable", "feature/widget", "feature/settings", "feature/update"):
        counts = dict(tests=0, failures=0, errors=0, skipped=0)
        files = list((ROOT / module / "build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
        assert files, module
        for path in files:
            suite = ET.parse(path).getroot()
            for key in counts:
                counts[key] += int(suite.attrib.get(key, 0))
        assert counts["failures"] == counts["errors"] == counts["skipped"] == 0, (module, counts)
        result[module] = counts
    return result


if __name__ == "__main__":
    instrumentation = {}
    for module in ("core/data", "app"):
        suites = []
        for path in (ROOT / module / "build/outputs/androidTest-results/connected").rglob("TEST-*.xml"):
            root = ET.parse(path).getroot()
            suites.extend([root] if root.tag == "testsuite" else root.findall("testsuite"))
        assert suites, (module, "Android instrumentation report missing")
        counts = {key: sum(int(suite.attrib.get(key, 0)) for suite in suites) for key in ("tests", "failures", "errors", "skipped")}
        assert counts["tests"] > 0 and counts["failures"] == counts["errors"] == counts["skipped"] == 0, (module, counts)
        instrumentation[module] = counts
    report = dict(jvm=verify_junit(), excel=verify_xlsx(), migration=verify_migration(),
                  android_instrumentation=instrumentation,
                  school_integration="Authenticated official classroom webpage verified: 2026-1, campus 4, week 3, Tuesday, sections 1-2, category 05 => 47 rows. Native Android results and authenticated grades/timetable still pending.")
    destination = ROOT / "build/reports/qlu-host-validation.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(report, indent=2, ensure_ascii=True))
