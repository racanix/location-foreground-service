// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "LocationForegroundService",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "LocationForegroundService",
            targets: ["LocationForegroundServicePlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .target(
            name: "LocationForegroundServicePlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/LocationForegroundServicePlugin"),
        .testTarget(
            name: "LocationForegroundServicePluginTests",
            dependencies: ["LocationForegroundServicePlugin"],
            path: "ios/Tests/LocationForegroundServicePluginTests")
    ]
)