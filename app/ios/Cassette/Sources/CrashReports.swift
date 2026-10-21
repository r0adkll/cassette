import CassetteKit
import FirebaseCore
import FirebaseCrashlytics
import Foundation

/// Crash reports (decision 0020): Firebase runs only in Release builds that carry its config, which
/// the build copies in when it's there. Collection starts off (Info.plist) and follows Settings →
/// About → Send crash reports once Kotlin reads it.
enum CrashReports {
  /// Configures Firebase and returns Crashlytics' switch, or nil when this build doesn't report.
  static func start() -> ((KotlinBoolean) -> Void)? {
    #if DEBUG
      return nil
    #else
      guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
        return nil
      }
      FirebaseApp.configure()
      return { enabled in
        let crashlytics = Crashlytics.crashlytics()
        crashlytics.setCrashlyticsCollectionEnabled(enabled.boolValue)
        // Reports kept while it was off never go out later.
        if !enabled.boolValue { crashlytics.deleteUnsentReports() }
      }
    #endif
  }
}
