import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
    
    init() {
        AdsBridge.register()
        // IAP-0: wire the Swift StoreKit-2 billing bridge into the Kotlin BillingProvider seam.
        if #available(iOS 15.0, *) {
            BillingBridge.register()
        }
    }
}
