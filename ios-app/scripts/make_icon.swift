// Code-drawn native calendar icon. No external image assets or runtime dependencies.
import AppKit
import Foundation

let directory = URL(fileURLWithPath: "Resources/Assets.xcassets/AppIcon.appiconset", isDirectory: true)
try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
let specs: [(String, Double, [Int])] = [("iphone",20,[2,3]),("iphone",29,[2,3]),("iphone",40,[2,3]),("iphone",60,[2,3]),("ipad",20,[1,2]),("ipad",29,[1,2]),("ipad",40,[1,2]),("ipad",76,[1,2]),("ipad",83.5,[2]),("ios-marketing",1024,[1])]
var images: [[String:String]] = []
for (idiom, size, scales) in specs {
    for scale in scales {
        let pixels = Int(size * Double(scale)), filename = "calendar-\(pixels).png"
        let bitmap = NSBitmapImageRep(bitmapDataPlanes:nil,pixelsWide:pixels,pixelsHigh:pixels,bitsPerSample:8,samplesPerPixel:4,hasAlpha:true,isPlanar:false,colorSpaceName:.deviceRGB,bytesPerRow:0,bitsPerPixel:0)!
        NSGraphicsContext.saveGraphicsState()
        let context = NSGraphicsContext(bitmapImageRep:bitmap)!
        NSGraphicsContext.current = context
        context.cgContext.scaleBy(x:CGFloat(pixels)/1024,y:CGFloat(pixels)/1024)
        NSColor(red:0.35,green:0.52,blue:0.72,alpha:1).setFill(); NSBezierPath(rect:NSRect(x:0,y:0,width:1024,height:1024)).fill()
        NSColor(red:0.94,green:0.96,blue:1,alpha:1).setFill(); NSBezierPath(roundedRect:NSRect(x:155,y:180,width:714,height:650),xRadius:70,yRadius:70).fill()
        NSColor(red:0.74,green:0.81,blue:0.94,alpha:1).setFill(); NSBezierPath(roundedRect:NSRect(x:205,y:675,width:614,height:105),xRadius:30,yRadius:30).fill()
        for (i, rgb) in [(0,[0.89,0.46,0.61]),(1,[0.43,0.64,0.84]),(2,[0.88,0.67,0.39])] {
            NSColor(red:rgb[0],green:rgb[1],blue:rgb[2],alpha:1).setFill()
            NSBezierPath(roundedRect:NSRect(x:218+i*210,y:290+(i==1 ? 70:0),width:165,height:280),xRadius:23,yRadius:23).fill()
        }
        NSGraphicsContext.restoreGraphicsState()
        try bitmap.representation(using:.png,properties:[:])!.write(to:directory.appendingPathComponent(filename))
        let point = size == floor(size) ? String(Int(size)) : String(size)
        images.append(["idiom":idiom,"size":"\(point)x\(point)","scale":"\(scale)x","filename":filename])
    }
}
let json = try JSONSerialization.data(withJSONObject:["images":images,"info":["author":"QluCampus","version":1]],options:[.prettyPrinted,.sortedKeys])
try json.write(to:directory.appendingPathComponent("Contents.json"))
