# Architecture

Blue nodes are primary components; the rest are secondary.

## High-level architecture

```mermaid
flowchart LR
    rider(["Rider<br/>gloves + handlebar remote"])

    subgraph app["motoTracklog"]
        direction TB
        subgraph screens["Screens"]
            record["RecordActivity<br/>status, live values, controls,<br/>settings mode"]
            runs["RunsActivity<br/>run list, delete, merge"]
            export["ExportActivity<br/>filters, preview, share"]
        end
        subgraph core["Recording"]
            service["LoggingService<br/>foreground, gps-logger thread"]
            bt["BluetoothNmeaSource<br/>own reader thread"]
            boot["BootReceiver"]
            holder["LoggingStateHolder<br/>StateFlow"]
        end
        subgraph data["Data"]
            repo["RunRepository<br/>RunWriter / RunReader"]
            stores[("Preference stores")]
            gpx["PointFilter<br/>GpxExporter"]
            update["UpdateChecker"]
        end
        record -- start / pause / stop intents --> service
        service -- throttled state --> holder
        holder --> record
        bt -- fixes --> service
        boot -- resume --> service
        service --> repo
        runs --> repo
        export --> gpx --> repo
    end

    subgraph android["Android"]
        lm["LocationManager<br/>GPS_PROVIDER, GNSS status"]
        btstack["Bluetooth RFCOMM"]
        notif["Notification"]
        share["Share sheet"]
        installer["Installer"]
    end

    receiver["Bluetooth NMEA receiver"]
    gh["GitHub releases<br/>and dev pre-release"]
    ci["GitHub Actions"]

    rider --> screens
    rider -. Pause / Resume / Stop .-> notif
    notif --> service
    service --> lm
    bt --> btstack --> receiver
    export --> share
    update -- download --> gh
    update -- install --> installer
    ci -- publishes APK --> gh

    classDef primary fill:#2F6FED,stroke:#1E4FB0,color:#FFFFFF
    classDef external fill:#EEF1F5,stroke:#9AA5B4,color:#14181D
    class record,service,repo,holder primary
    class receiver,gh,ci,rider external
```

## Layers

```mermaid
flowchart TB
    subgraph screens["Screens"]
        direction LR
        record["RecordActivity"] ~~~ runs["RunsActivity"] ~~~ export["ExportActivity"]
    end
    subgraph widgets["UI components"]
        direction LR
        tiles["TileAdapter<br/>item_tile"] ~~~ rows["ListRowAdapter<br/>item_list_row"] ~~~ stat["view_stat_tile"] ~~~ dialogs["OptionsDialog<br/>UpdateFlow"]
    end
    subgraph input["Input, window and text"]
        direction LR
        keys["KeyInput<br/>blockKeyLongPress<br/>EscapeKeys"] ~~~ touch["TouchOnlyRow"] ~~~ immersive["Immersive<br/>LayoutUtils"] ~~~ text["Format<br/>RecordStatus<br/>RunText"]
    end
    subgraph recording["Recording"]
        direction LR
        service["LoggingService"] ~~~ bt["BluetoothNmeaSource<br/>NmeaDebugLog"] ~~~ holder["LoggingStateHolder"] ~~~ boot["BootReceiver"]
    end
    subgraph data["Data"]
        direction LR
        repo["RunRepository<br/>RunWriter / RunReader<br/>RunCodec"] ~~~ nmea["NmeaParser"] ~~~ exp["PointFilter<br/>GpxExporter"] ~~~ stores["Preference stores"] ~~~ updater["UpdateChecker"]
    end
    subgraph platform["Android platform"]
        direction LR
        p1["LocationManager"] ~~~ p2["Bluetooth"] ~~~ p3["SharedPreferences<br/>files"] ~~~ p4["Notifications<br/>FileProvider"] ~~~ p5["HttpURLConnection"]
    end

    screens --> widgets --> input --> data --> platform
    screens --> recording --> data
    recording --> platform

    classDef primary fill:#2F6FED,stroke:#1E4FB0,color:#FFFFFF
    class record,service,repo,holder primary
```

## Persistent state

There is no database. A run is a binary file; settings and the active-run marker are
`SharedPreferences`, shown here as entities.

```mermaid
erDiagram
    run_file {
        string name "filesDir/runs/startMillis.dat; the name is the run id and start time"
        bytes header "16 bytes: magic GPSL, version 1, record size 68"
    }
    record {
        int flags "bitmask of the optional fields present"
        long timeMillis "UTC fix time"
        long elapsedRealtimeNanos "monotonic clock"
        double latitude
        double longitude
        double altitude "NaN when absent"
        float accuracy "NaN when absent; HDOP x 5 m from NMEA"
        float verticalAccuracy "NaN when absent"
        float speed "m/s, NaN when absent"
        float speedAccuracy "NaN when absent"
        float bearing "NaN when absent"
        float bearingAccuracy "NaN when absent"
    }
    recording {
        long active_run_id "absent when no run is active"
        boolean active_run_paused "default false"
    }
    settings {
        string source_bluetooth_address "absent for the internal GPS"
        string source_bluetooth_name
        boolean debug_nmea_log "default false"
        int filter_accuracy_m "default 10"
        float filter_distance_m "default 0"
        int filter_time_s "default 0"
        boolean units_metric "default true"
        boolean dark_theme "default true"
        int orientation "default FULL_SENSOR"
        boolean show_nav_bar "default false"
    }
    debug_log {
        string name "cacheDir/debug/nmea-runId.log"
    }
    run_file ||--o{ record : "appends, fixed size"
    recording |o--o| run_file : "active_run_id names"
    debug_log |o--|| run_file : "captured during"
```
