# AppStateMachine.State names are persisted across launches. Keep the enum
# stable so an app update can restore state written by an older version.
-keep enum com.neo.ezaccounting.AppStateMachine$State { *; }
