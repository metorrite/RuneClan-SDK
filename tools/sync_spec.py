#!/usr/bin/env python3
"""Copy the hub's OpenAPI spec into spec/openapi.json, keeping only what the public SDK covers.

    python3 tools/sync_spec.py <path to RuneClan-API>/src/main/resources/openapi.json [--source <commit>]

Keeps the operations marked x-runeclan-audience: public, the components they reference (transitively),
the apiKey security scheme and the error code list. First-party and plugin routes never land in this
public repo. Run tools/gen_model.py afterwards; the tests fail when the two drift apart.
"""
import argparse
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "spec" / "openapi.json"
METHODS = {"get", "put", "post", "delete", "patch", "head", "options"}


def refs(node, found):
    if isinstance(node, dict):
        ref = node.get("$ref")
        if isinstance(ref, str) and ref.startswith("#/components/"):
            found.add(tuple(ref[len("#/components/"):].split("/", 1)))
        for value in node.values():
            refs(value, found)
    elif isinstance(node, list):
        for value in node:
            refs(value, found)
    return found


def public_subset(spec):
    paths = {}
    for path, item in spec["paths"].items():
        ops = {m: op for m, op in item.items() if m in METHODS and op.get("x-runeclan-audience") == "public"}
        if ops:
            shared = {k: v for k, v in item.items() if k not in METHODS}
            paths[path] = {**shared, **ops}
    comps = spec.get("components", {})
    wanted = set()
    todo = list(refs(paths, set()))
    while todo:
        key = todo.pop()
        if key in wanted:
            continue
        wanted.add(key)
        kind, name = key
        todo.extend(refs(comps[kind][name], set()))
    out_comps = {}
    for kind, name in sorted(wanted):
        out_comps.setdefault(kind, {})[name] = comps[kind][name]
    out_comps["securitySchemes"] = {"apiKey": comps["securitySchemes"]["apiKey"]}
    used_tags = {t for item in paths.values() for m, op in item.items() if m in METHODS for t in op.get("tags", [])}
    out = {
        "openapi": spec["openapi"],
        "info": spec["info"],
        "servers": spec.get("servers", []),
        "security": spec.get("security", []),
        "tags": [t for t in spec.get("tags", []) if t.get("name") in used_tags],
        "paths": paths,
        "components": out_comps,
        "x-runeclan-error-codes": spec.get("x-runeclan-error-codes", []),
    }
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("hub_spec")
    ap.add_argument("--source", help="hub commit the spec came from")
    args = ap.parse_args()
    spec = json.loads(pathlib.Path(args.hub_spec).read_text(encoding="utf-8"))
    out = public_subset(spec)
    if args.source:
        out["info"] = {**out["info"], "x-runeclan-source": {"repo": "RuneClan-API", "commit": args.source}}
    OUT.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    ops = sum(1 for item in out["paths"].values() for m in item if m in METHODS)
    print(f"spec/openapi.json: {ops} public operations, {len(out['components'].get('schemas', {}))} schemas")


if __name__ == "__main__":
    sys.exit(main())
