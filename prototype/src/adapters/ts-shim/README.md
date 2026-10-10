# TS-Shim adapter boundary

This directory is reserved for the future EpicHub Pages ↔ TS-Shim external interoperability adapter.

The adapter must implement typed prototype ports such as `RuntimeSource`. Feature code must not import shim transport or wire-schema details directly.

Expected ownership here, once the exact shim contract is reviewed:

- endpoint configuration/discovery;
- connection/reconnect state;
- WebSocket/HTTP/other transport mechanics;
- wire schema/version negotiation;
- serialization/deserialization;
- capability discovery;
- staleness/disconnect/error translation;
- conversion into transport-neutral prototype models.

Do **not** add ECU page/offset/byte mutation directly to browser features. Initial integration should remain read-only until mutation identity, safety and verification contracts are explicitly accepted.

If TS-Shim uses JSON or another serialized representation, that is an external tooling interoperability boundary under D-023. It does not select a production Android runtime representation.
