# Breeze CHMS requests (resterm)

Translated from `../Breeze CHMS - SGC.postman_collection.json`.
Because Postman has become so evil.

[Breeze API Doc](https://app.breezechms.com/api)


```sh
./open.sh              # opens checkin.http in the TUI
./open.sh people.http  # start somewhere else
```

In the TUI: `Ctrl+Enter` runs the request under the cursor, `Ctrl+E` switches
environment, `/` filters the navigator (by name, URL, or tag), `?` shows help.

Headless, for one request or a whole file:

```sh
resterm run checkin.http -l 24 -e sgc     # run the request at that line
resterm run profile.http -a -e sgc        # run every request in the file
```

## Files

| File | Contents |
| --- | --- |
| `checkin.http` | Eligible people, attendance list, check in / check out |
| `people.http` | People CRUD, family roles, create family |
| `events.http` | v1 event list, v2 event-instances |
| `profile.http` | Profile field definitions, tags |
| `resterm.env.json` | `sgc` (live) and `mock` (localhost) environments |
| `rts/instances.rts` | Computes the Sunday event-instance id |

## Environments

`sgc` points at the live account. `mock` points at `127.0.0.1:8080`, which is
where `resterm mock` serves, and is also a handy way to see a request's fully
resolved URL without sending it:

```sh
resterm run people.http -a -e mock -t 2s   # every URL, nothing sent
```

The v1 API key is in `resterm.env.json`; update the readacted version.
The v2 endpoint uses a JWT; add it to the environment like so:

```sh
export BREEZE_V2_TOKEN='eyJ0eXAi...'
```

## Computing Instance IDs 

See `rts/instances.rts`; it's based on  `SettingsService.updateBreezeInstanceId()` in the app.
The `checkin.http` file references it; you can update to 
things like `instances.weeksAgo(1)` of `instances.forSunday("2026-09-06")`.

```
# @file instanceId {{= instances.current() }}
```
