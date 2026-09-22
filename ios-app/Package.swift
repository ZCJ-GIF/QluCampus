// swift-tools-version: 5.9
// QluCampus iOS, GPL-3.0. School rules ported from the Android fork.
import PackageDescription

let package = Package(
    name: "CampusCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [.library(name: "CampusCore", targets: ["CampusCore"])],
    dependencies: [
        .package(url: "https://github.com/weichsel/ZIPFoundation.git", exact: "0.9.20"),
        .package(url: "https://github.com/scinfu/SwiftSoup.git", exact: "2.8.8")
    ],
    targets: [
        .target(name: "CampusCore", dependencies: ["ZIPFoundation", "SwiftSoup"]),
        .testTarget(name: "CampusCoreTests", dependencies: ["CampusCore"])
    ]
)
