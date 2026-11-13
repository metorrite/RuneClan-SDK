#!/usr/bin/env python3
"""Build the SDK's API reference: docs/index.html, one self-contained page (no internet, no build step to read it).

    python3 tools/gen_docs.py            # write docs/index.html
    python3 tools/gen_docs.py --check    # exit 1 when docs/index.html differs from what the sources give

Sources: the friendly methods and their Javadoc (src/main/java/com/runeclans/sdk/*.java), the routes (Routes.java and
spec/openapi.json, public operations only), the examples/ folder, and the hand-written guide words in tools/docs_content.py.
Open the page in any browser, or run `java -cp runeclan-sdk-<version>.jar com.runeclans.sdk.Docs` (the jar carries a copy).
"""
import html
import json
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import docs_content as C  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parent.parent
SPEC = ROOT / "spec" / "openapi.json"
PKG = ROOT / "src" / "main" / "java" / "com" / "runeclans" / "sdk"
MODEL = PKG / "model"
EXAMPLES = ROOT / "examples"
OUT = ROOT / "docs" / "index.html"
METHODS = ["get", "put", "post", "delete", "patch"]
SCOPE_RE = re.compile(r"Needs the scope `([a-z:_]+)`\.?(?: \(or ([a-z:_]+)\))?\.?")

esc = html.escape


# ---------------------------------------------------------------------------------------------------------------- highlighting
JAVA_KW = set("""abstract boolean break byte case catch char class continue default do double else enum extends final finally float for if
implements import instanceof int interface long new null package private protected public return short static super switch this throw
throws try var void while true false record""".split())
TOKEN = re.compile(r'(?P<c>//[^\n]*|/\*.*?\*/)|(?P<s>"(?:[^"\\\n]|\\.)*")|(?P<n>\b\d[\d_]*(?:\.\d+)?[Ll]?\b)|(?P<w>[A-Za-z_]\w*)|(?P<o>.)',
                   re.S)


def hl_java(code):
    out = []
    for m in TOKEN.finditer(code):
        t = m.group(0)
        if m.group("c"):
            out.append(f'<span class="tc">{esc(t)}</span>')
        elif m.group("s"):
            out.append(f'<span class="ts">{esc(t)}</span>')
        elif m.group("n"):
            out.append(f'<span class="tn">{esc(t)}</span>')
        elif m.group("w"):
            if t in JAVA_KW:
                out.append(f'<span class="tk">{t}</span>')
            elif t[0].isupper():
                out.append(f'<span class="tt">{t}</span>')
            else:
                out.append(t)
        else:
            out.append(esc(t))
    return "".join(out)


SHELL_TOKEN = re.compile(r"(?P<s>'[^']*'|\"(?:[^\"\\]|\\.)*\")|(?P<v>\$[A-Z_]+)|(?P<f>(?<=\s)-{1,2}[A-Za-z][\w-]*)|(?P<k>^curl\b)|(?P<o>.)",
                         re.S | re.M)


def hl_shell(code):
    out = []
    for m in SHELL_TOKEN.finditer(code):
        t = esc(m.group(0))
        if m.group("s"):
            t = re.sub(r"(\$[A-Z_]+)", r'<span class="tn">\1</span>', t)
            out.append(f'<span class="ts">{t}</span>')
        elif m.group("v"):
            out.append(f'<span class="tn">{t}</span>')
        elif m.group("f"):
            out.append(f'<span class="tt">{t}</span>')
        elif m.group("k"):
            out.append(f'<span class="tk">{t}</span>')
        else:
            out.append(t)
    return "".join(out)


def hl_json(code):
    s = esc(code)
    s = re.sub(r"(&quot;[^&]*?&quot;)(\s*:)", r'<span class="tt">\1</span>\2', s)
    s = re.sub(r"(:\s*)(&quot;[^&]*?&quot;)", r'\1<span class="ts">\2</span>', s)
    s = re.sub(r"(:\s*)(-?\d+(?:\.\d+)?|true|false|null)\b", r'\1<span class="tn">\2</span>', s)
    return s


def code_block(code, lang="java"):
    body = {"java": hl_java, "shell": hl_shell, "json": hl_json}[lang](code.strip("\n"))
    return f'<div class="code"><button class="copy" type="button" aria-label="Copy">Copy</button><pre><code>{body}</code></pre></div>'


def inline(text):
    """Spec/Javadoc prose: `code` and {@code x} to <code>, everything else escaped."""
    text = re.sub(r"\{@(?:code|link|linkplain) ([^}]*)\}", lambda m: "`" + m.group(1).replace("#", ".").strip() + "`", text)
    parts = re.split(r"(`[^`]+`)", text)
    return "".join(f"<code>{esc(p[1:-1])}</code>" if p.startswith("`") and p.endswith("`") and len(p) > 1 else esc(p) for p in parts)


def slug(text):
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


# ---------------------------------------------------------------------------------------------------------------- the Java side
METHOD_RE = re.compile(
    r"(?:/\*\*(?P<doc>(?:(?!\*/).)*?)\*/\s*)?(?:@\w+(?:\([^)]*\))?\s*)*public (?P<static>static )?(?P<ret>[\w<>\[\],.? ]+?) (?P<name>\w+)\((?P<params>[^)]*)\) \{",
    re.S)


def javadoc_text(doc):
    if not doc:
        return ""
    doc = re.sub(r"<pre>.*?</pre>", "", doc, flags=re.S)
    lines = [re.sub(r"^\s*\*\s?", "", ln) for ln in doc.strip().splitlines()]
    return re.sub(r"\s+", " ", " ".join(lines)).strip()


def body_of(src, start):
    depth, i = 0, start
    while i < len(src):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return src[start:i]
        i += 1
    return src[start:]


def read_methods(cls):
    src = (PKG / f"{cls}.java").read_text(encoding="utf-8")
    out = []
    for m in METHOD_RE.finditer(src):
        if m.group("ret").strip() in ("class", "record", "final class") or " class " in m.group(0):
            continue
        name = m.group("name")
        if cls == "RuneClanClient" and not m.group("params").strip() and m.group("ret").strip() in {g[1] for g in C.GROUPS[1:]}:
            continue  # a group accessor such as players()
        body = body_of(src, m.end() - 1)
        out.append({
            "cls": cls, "name": name, "static": bool(m.group("static")), "ret": m.group("ret").strip(),
            "params": re.sub(r"\s+", " ", m.group("params").strip()), "doc": javadoc_text(m.group("doc")),
            "routes": sorted(set(re.findall(r"Routes\.([A-Z0-9_]+)", body))),
        })
    return out


def read_routes():
    src = (PKG / "Routes.java").read_text(encoding="utf-8")
    routes = {}
    for m in re.finditer(r'public static final Route (\w+) = new Route\("(\w+)", "([^"]+)", List\.of\(([^)]*)\), (true|false), ([^,]+), ([^)]+?)\);', src):
        name, method, path, scopes, keyed, media, typ = m.groups()
        typ = typ.strip()
        routes[(method, path)] = {
            "const": name, "keyed": keyed == "true",
            "type": None if typ == "null" else typ.replace("com.runeclans.sdk.model.", "").replace(".class", ""),
        }
    return routes


# ---------------------------------------------------------------------------------------------------------------- the spec side
class Spec:
    def __init__(self, spec):
        self.spec = spec
        self.schemas = spec["components"]["schemas"]
        self.models = {p.stem for p in MODEL.glob("*.java")}
        self.codes = {e["code"]: e for e in spec.get("x-runeclan-error-codes", [])}

    def deref(self, obj):
        while isinstance(obj, dict) and "$ref" in obj:
            parts = obj["$ref"].lstrip("#/").split("/")
            node = self.spec
            for p in parts:
                node = node[p]
            obj = node
        return obj

    def ref_name(self, schema):
        if isinstance(schema, dict) and "$ref" in schema:
            return schema["$ref"].rsplit("/", 1)[-1]
        return None

    def merged(self, schema):
        schema = self.deref(schema)
        if "allOf" not in schema:
            return schema
        props, req, desc = {}, [], schema.get("description")
        for part in schema["allOf"]:
            part = self.merged(part)
            props.update(part.get("properties", {}))
            req += part.get("required", [])
            desc = desc or part.get("description")
        return {"type": "object", "properties": props, "required": req, "description": desc}

    def type_label(self, schema):
        """A short type name with a link for named types."""
        name = self.ref_name(schema)
        if name:
            return f'<a href="#t-{name}">{esc(name)}</a>' if name in self.schemas else esc(name)
        s = self.deref(schema)
        for k in ("oneOf", "anyOf"):
            if k in s:
                return " | ".join(self.type_label(x) for x in s[k])
        t = s.get("type")
        if isinstance(t, list):
            t = " | ".join(x for x in t)
        if t == "array":
            return f"list of {self.type_label(s.get('items', {}))}"
        if t == "object" and "additionalProperties" in s and not s.get("properties"):
            ap = s["additionalProperties"]
            return f"map of {self.type_label(ap) if isinstance(ap, dict) else 'any'}"
        if "allOf" in s:
            return "object"
        label = t or "any"
        if s.get("format"):
            label += f" ({s['format']})"
        return esc(label)

    def fields(self, schema, depth=0, seen=()):
        """Rows (depth, name, type html, required, description html) for an object's fields; nested objects go one level deeper."""
        s = self.merged(schema)
        rows = []
        if s.get("type") == "array" or (isinstance(s.get("type"), list) and "array" in s["type"]):
            s = self.merged(s.get("items", {}))
        req = set(s.get("required", []))
        for name, prop in (s.get("properties") or {}).items():
            p = self.deref(prop)
            desc = prop.get("description") or p.get("description") or ""
            enum = p.get("enum") or (self.deref(p.get("items", {})).get("enum") if p.get("type") == "array" else None)
            d = inline(desc)
            if enum:
                if d and not d.rstrip().endswith((".", ")", ":")):
                    d += "."
                d += (" " if d else "") + "One of: " + ", ".join(f"<code>{esc(str(e))}</code>" for e in enum if e is not None) + "."
            rows.append((depth, name, self.type_label(prop), name in req, d))
            if self.ref_name(prop) is None and depth < 3:
                inner = p if p.get("type") != "array" else self.deref(p.get("items", {}))
                if self.ref_name(p.get("items", {}) if p.get("type") == "array" else {}) is None and (inner.get("properties") or "allOf" in inner):
                    rows += self.fields(inner, depth + 1, seen)
        return rows

    def sample(self, schema, depth=0, stack=()):
        name = self.ref_name(schema)
        if name in stack or depth > 5:
            return {}
        stack = stack + ((name,) if name else ())
        s = self.merged(schema)
        for k in ("oneOf", "anyOf"):
            if k in s:
                return self.sample(s[k][0], depth, stack)
        if "example" in s:
            return s["example"]
        t = s.get("type")
        if isinstance(t, list):
            t = next((x for x in t if x != "null"), "null")
        if "enum" in s:
            return next((e for e in s["enum"] if e is not None), None)
        if t == "object" or s.get("properties"):
            return {k: self.sample(v, depth + 1, stack) for k, v in (s.get("properties") or {}).items()}
        if t == "array":
            return [self.sample(s.get("items", {}), depth + 1, stack)]
        if t == "integer":
            return 0
        if t == "number":
            return 0.0
        if t == "boolean":
            return False
        if t == "string":
            fmt = s.get("format")
            return {"date-time": "2026-01-01T00:00:00Z", "date": "2026-01-01", "uri": "https://example.org"}.get(fmt, "string")
        if t == "null":
            return None
        return {}


# ---------------------------------------------------------------------------------------------------------------- page parts
def camel(name):
    parts = re.split(r"[^A-Za-z0-9]+", name)
    return parts[0][:1].lower() + parts[0][1:] + "".join(p[:1].upper() + p[1:] for p in parts[1:])


def env_name(name):
    return re.sub(r"(?<!^)(?=[A-Z])", "_", camel(name)).upper()


def route_java(sp, op_method, path, op, info):
    """A generic example: hub.route(Routes.X) with the path, the required query settings and the body."""
    lines = [f"hub.route(Routes.{info['const']})"]
    for prm in params_of(sp, op):
        if prm["in"] == "path":
            lines.append(f'.path("{prm["name"]}", {camel(prm["name"])})')
        elif prm["in"] == "query" and prm.get("required"):
            lines.append(f'.query("{prm["name"]}", {camel(prm["name"])})')
    if "requestBody" in op:
        schema = sp.merged(next(iter(op["requestBody"].get("content", {}).values()), {}).get("schema", {}))
        req = schema.get("required") or list((schema.get("properties") or {}).keys())[:2]
        if req:
            lines.append(".body(Map.of(" + ", ".join(f'"{r}", {camel(r)}' for r in req[:4]) + "))")
        else:
            lines.append(".body(Map.of())")
    if info["type"]:
        lines.append(f".as({info['type']}.class)")
    elif ok_media(op) in ("application/json",):
        lines.append(".asJson()")
    elif ok_media(op):
        lines.append(".asBytes()")
    else:
        lines.append(".send()")
    lines.append(".complete();")
    head = "var answer = " if lines[-2] != ".send()" else ""
    return head + lines[0] + "\n" + "\n".join("        " + ln for ln in lines[1:])


def ok_media(op):
    for code, r in op["responses"].items():
        if code.startswith("2"):
            r = r if "$ref" not in r else {}
            return next(iter(r.get("content", {})), None)
    return None


def ok_schema(sp, op):
    for code, r in op["responses"].items():
        if code.startswith("2"):
            r = sp.deref(r)
            c = r.get("content", {})
            if "application/json" in c:
                return code, r.get("description", ""), c["application/json"].get("schema")
            return code, r.get("description", ""), None
    return None, "", None


def params_of(sp, op):
    return [sp.deref(p) for p in op.get("parameters", [])]


def route_curl(sp, method, path, op, keyed):
    url = "$RUNECLAN_API_URL" + re.sub(r"\{(\w+)\}", lambda m: "$" + env_name(m.group(1)), path)
    req_q = [p for p in params_of(sp, op) if p["in"] == "query" and p.get("required")]
    if req_q:
        url += "?" + "&".join(f"{p['name']}=${env_name(p['name'])}" for p in req_q)
    parts = ["curl -s"] + ([f"-X {method}"] if method != "GET" else []) + [f'"{url}"']
    lines = [" ".join(parts)]
    if keyed:
        lines.append('-H "Authorization: Bearer $RUNECLAN_API_KEY"')
    if "requestBody" in op:
        schema = next(iter(op["requestBody"].get("content", {}).values()), {}).get("schema", {})
        body = sp.sample(schema)
        lines.append('-H "Content-Type: application/json"')
        lines.append("-d '" + json.dumps(body, separators=(",", ":")) + "'")
    return " \\\n  ".join(lines)


def route_errors(sp, op, keyed, scopes, has_path):
    found = []

    def add(status, code, why):
        if code not in [f[1] for f in found]:
            found.append((status, code, why))

    if keyed:
        add(401, "bad_key", "No key, or the key is wrong or revoked.")
    if scopes:
        add(403, "missing_scope", "The key lacks " + " or ".join(scopes) + ".")
    for status, code in re.findall(r"\b([1-5]\d\d) ([a-z][a-z_]+)\b", op.get("description", "")):
        if code in sp.codes:
            add(int(status), code, sp.codes[code].get("meaning", ""))
    if has_path:
        add(404, "not_found", "Doesn't exist, or isn't yours to see.")
    for code in re.findall(r"`([a-z][a-z_]+)`", op.get("description", "")):
        if code in sp.codes and code not in [f[1] for f in found]:
            add(sp.codes[code]["statuses"][0], code, sp.codes[code].get("meaning", ""))
    if keyed:
        add(429, "rate_limited", "Too fast; the SDK waits Retry-After and sends it again.")
        add(426, "sdk_too_old", "This SDK version is no longer served.")
    return found


def table(head, rows):
    th = "".join(f"<th>{h}</th>" for h in head)
    trs = "".join("<tr>" + "".join(f"<td>{c}</td>" for c in r) + "</tr>" for r in rows)
    return f'<div class="tablewrap"><table><thead><tr>{th}</tr></thead><tbody>{trs}</tbody></table></div>'


def field_table(sp, rows):
    if not rows:
        return '<p class="muted">No fields listed.</p>'
    out = []
    for depth, name, typ, req, desc in rows:
        pad = f' style="padding-left:{0.75 + depth * 1.1:.2f}rem"' if depth else ""
        mark = ' <span class="req">required</span>' if req else ""
        out.append((f'<code{pad and ""}>{esc(name)}</code>{mark}', typ, desc, pad))
    trs = "".join(f"<tr><td{p}>{a}</td><td>{b}</td><td>{c}</td></tr>" for a, b, c, p in out)
    return f'<div class="tablewrap"><table class="fields"><thead><tr><th>Field</th><th>Type</th><th>What it is</th></tr></thead><tbody>{trs}</tbody></table></div>'


def badge(method):
    return f'<span class="verb v-{method.lower()}">{method}</span>'


def scope_chips(scopes):
    if not scopes:
        return '<span class="chip any">any key</span>'
    return " or ".join(f'<a class="chip" href="#scopes">{esc(s)}</a>' for s in scopes)


# ---------------------------------------------------------------------------------------------------------------- build
def build():
    spec = json.loads(SPEC.read_text(encoding="utf-8"))
    sp = Spec(spec)
    routes = read_routes()
    version = re.search(r'VERSION = "([^"]+)"', (PKG / "RuneClanClient.java").read_text(encoding="utf-8")).group(1)
    source = spec["info"].get("x-runeclan-source", {}).get("commit", "?")
    problems = []

    # operations, in spec order
    ops = []
    for path, item in spec["paths"].items():
        for method in METHODS:
            op = item.get(method)
            if not isinstance(op, dict) or op.get("x-runeclan-audience", "public") != "public":
                continue
            M = method.upper()
            info = routes.get((M, path))
            if info is None:
                problems.append(f"{M} {path} is in the spec but not in Routes.java (run tools/gen_model.py)")
                continue
            scopes = [s for g in SCOPE_RE.findall(op.get("description", "")) for s in g if s]
            ops.append({"method": M, "path": path, "op": op, "info": info, "scopes": scopes,
                        "tag": (op.get("tags") or ["Other"])[0]})

    # SDK methods
    groups = []
    by_route = {}
    for accessor, cls, blurb in C.GROUPS:
        methods = [m for m in read_methods(cls) if f"{cls}.{m['name']}" not in C.HIDDEN]
        merged = {}
        for m in methods:
            merged.setdefault(m["name"], []).append(m)
        groups.append((accessor, cls, blurb, merged))
        for name, overloads in merged.items():
            key = f"{cls}.{name}"
            if key not in C.EXAMPLES:
                problems.append(f"tools/docs_content.py has no example for {key}")
            for m in overloads:
                for r in m["routes"]:
                    by_route.setdefault(r, []).append((accessor, cls, name))
    known = {f"{cls}.{n}" for _, cls, _, ms in groups for n in ms}
    for key in C.EXAMPLES:
        if key not in known:
            problems.append(f"tools/docs_content.py has an example for {key}, which isn't a public SDK method")
    const_to_op = {o["info"]["const"]: o for o in ops}

    nav, main = [], []

    # ---- guide
    guide = []
    guide.append(("start", "Quick start", f"""
<p class="lead">Talk to the RuneClans hub from your Discord bot (or anything on Java 17+). The SDK handles the key, the hub's rate limits
and retries, so your code is just the calls.</p>
<ol class="steps">
<li><b>Add the SDK</b> (JitPack, from this repository's tags):
{code_block('''<repositories>
    <repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
</repositories>
<dependency>
    <groupId>com.github.metorrite</groupId>
    <artifactId>runeclan-sdk</artifactId>
    <version>v''' + version + '''</version>
</dependency>''', "shell")}
<p>Gradle: <code>maven {{ url 'https://jitpack.io' }}</code> and <code>implementation 'com.github.metorrite:runeclan-sdk:v{version}'</code>.</p></li>
<li><b>Give it your key</b>: set <code>RUNECLAN_API_KEY=rsh_...</code>, or put <code>apiKey=rsh_...</code> in a <code>runeclan.properties</code> file next to your
bot (and add that file to <code>.gitignore</code>). No key yet? See <a href="#keys">Getting a key</a>.</li>
<li><b>Connect once and call</b>:
{code_block('''RuneClanClient hub = RuneClan.connect();   // checks the key with the hub

hub.players().byDiscordId(userId).queue(player -> {
    if (player.isEmpty()) event.reply("Link your RuneScape account first.").queue();
    else event.reply("You are " + player.get().characters().get(0).rsn()).queue();
});''')}</li>
</ol>
<p>Keep the one <code>hub</code> for your whole bot: it is safe to share between threads. Full programs to copy are under <a href="#examples">Examples</a>.</p>
"""))
    guide.append(("keys", "Getting a key", f"""
<p>Ask the hub for a key once. A person approves each request (and may grant fewer scopes than you asked for).</p>
{code_block('''curl -s -X POST "$RUNECLAN_API_URL/v1/apps/requests" \\
  -H "Content-Type: application/json" \\
  -d '{"name":"Clan Helper","description":"Shows our members' drops in our server.","contactEmail":"you@example.org",
       "discordApplicationId":"<your bot application id>","scopes":["players:read","events:read","guilds:write"]}' ''', "shell")}
<p>The answer has a <code>requestId</code> and a <code>claimSecret</code> (shown once: keep it). Once approved, collect the key:</p>
{code_block('''curl -s -X POST "$RUNECLAN_API_URL/v1/apps/requests/$REQUEST_ID/claim" \\
  -H "Content-Type: application/json" -d '{"claimSecret":"rshc_..."}' ''', "shell")}
<p><code>409 pending</code> means not decided yet, <code>403 refused</code>, <code>410 already_claimed</code> once collected. The answer holds
<code>apiKey</code> (<code>rsh_...</code>), shown once. Store it as a secret: never in git, never in a log.</p>
<p><code>RUNECLAN_API_URL</code> is <code>https://api.runeclans.com</code>; the SDK uses that address unless you set another.</p>
"""))
    guide.append(("calling", "Making calls", f"""
<p>Every method answers an <code>ApiAction</code>, like JDA's <code>RestAction</code>. Nothing is sent until you pick how to run it:</p>
{table(["You write", "What happens"], [
        ["<code>.complete()</code>", "Sends the call and waits for the answer. Use it on your own worker or virtual thread."],
        ["<code>.submit()</code>", "Gives a <code>CompletableFuture</code>."],
        ["<code>.queue(ok, failed)</code>", "Runs your callback when the answer comes. Never blocks a JDA listener. Without <code>failed</code>, a failure is logged."],
        ["<code>.map(f)</code>", "A new action that changes the answer before you get it."],
    ])}
<p><b>Lookups</b> (<code>get</code>, <code>byName</code>, <code>byDiscordId</code>, ...) answer an <code>Optional</code>: empty for "not found"
or "not yours to see". <b>Lists</b> come a page at a time (<code>limit</code> 1 to 200, a <code>cursor</code> for the next page); the
<code>all...()</code> methods follow the pages for you.</p>
<p><b>Players decide what you see.</b> A player shows up only after they grant your app access, as an alias (<code>a1_...</code>) only your
app gets; a Discord id only with their identity grant. A player can limit the grant to some servers: read for one server with
<code>hub.forGuild(guildId)</code>.</p>
<p><b>Any route</b>: every public operation is in <code>Routes</code>, so a route with no friendly method yet is still one call:</p>
{code_block('''EventStandingsAnswer table = hub.route(Routes.GET_EVENTS_EVENT_ID_STANDINGS)
        .path("eventId", eventId)
        .as(EventStandingsAnswer.class)
        .complete();''')}
{table(["On route(...)", "Does"], [
        ["<code>.path(name, value)</code>", "Fills <code>{name}</code> in the path (encoded for you)."],
        ["<code>.query(name, value)</code>", "Adds <code>?name=value</code>; a null value is left out."],
        ["<code>.header(name, value)</code> / <code>.guild(id)</code>", "Adds a header / <code>X-Guild-Id</code>."],
        ["<code>.body(object)</code>", "A JSON body (a <code>Map</code>, a record, a <code>JsonNode</code>)."],
        ["<code>.idempotent()</code>", "Adds an <code>Idempotency-Key</code> so a write is safe to send again (see <a href=\"#retries\">Retries</a>)."],
        ["<code>.as(Type.class)</code>, <code>.asOptional(..)</code>, <code>.asJson()</code>, <code>.asBytes()</code>, <code>.send()</code>", "How to read the answer: a type, an Optional (404 = empty), JSON, bytes, or nothing."],
    ])}
"""))
    scope_rows = []
    for s, words in C.SCOPES.items():
        used = [o for o in ops if s in o["scopes"]]
        links = ", ".join(f'<a href="#r-{o["info"]["const"]}">{o["method"]} {esc(o["path"])}</a>' for o in used[:6])
        more = f" and {len(used) - 6} more" if len(used) > 6 else ""
        scope_rows.append([f"<code>{esc(s)}</code>", esc(words), (links + more) or '<span class="muted">none yet</span>'])
    for o in ops:
        for s in o["scopes"]:
            if s not in C.SCOPES and not s.endswith(":write"):
                problems.append(f"tools/docs_content.py SCOPES lacks {s}")
    guide.append(("scopes", "Scopes", f"""
<p>Your key carries scopes. Each call needs the one listed beside it (routes marked <span class="chip any">any key</span> need none). The SDK
knows your scopes from the start-up handshake, so a call your key can't make fails at once (<code>missing_scope</code>, <code>local()</code>
true) instead of being sent. Got a new scope? <code>hub.refreshHandshake()</code>.</p>
{table(["Scope", "Lets you", "Used by"], scope_rows)}
"""))
    guide.append(("limits", "Rate limits", f"""
<p>Limits are per key, as a bucket that refills every minute:</p>
{table(["Tier", "Calls a minute", "Burst", ""], [[f"<code>{t}</code>", str(a), str(b), esc(w)] for t, a, b, w in C.TIERS])}
<p>Your key's real numbers come from the handshake (<code>hub.handshake().get().rateLimit()</code>). The SDK paces itself to them, so it waits
for a free slot instead of sending a call it knows will be refused. Every answer carries <code>RateLimit-Limit</code>,
<code>RateLimit-Remaining</code> and <code>RateLimit-Reset</code>, which keep the pacing honest when several copies of your bot share one key.</p>
"""))
    guide.append(("retries", "Retries and outages", """
<ul>
<li><b>429</b>: the SDK waits the hub's <code>Retry-After</code> (up to 60 s, set <code>maxRetryWait</code>) and sends the call again. A longer
wait comes to you as <code>rate_limited</code> with <code>retryAfter()</code>.</li>
<li><b>Lost connection, 502, 503, 504</b>: reads and <code>PUT</code>s are sent again (3 tries in all, with backoff).</li>
<li><b><code>POST</code> and <code>DELETE</code> are never sent again blind</b>: the first one may have worked. Mark a write with
<code>.idempotent()</code> on a <code>route(...)</code> call and it becomes safe to repeat: the hub answers a repeat within 24 hours with the
first answer instead of doing it twice.</li>
</ul>
<p>The hub checks every key, scope and limit itself: the SDK's checks only save you wasted calls.</p>
"""))
    code_rows = [[f"<code>{esc(c)}</code>", esc(w)] for c, w in C.SDK_ERRORS]
    all_codes = [[f"<code>{esc(e['code'])}</code>", ", ".join(str(s) for s in e.get("statuses", [])), inline(e.get("meaning", ""))]
                 for e in spec.get("x-runeclan-error-codes", [])]
    guide.append(("errors", "Errors", f"""
<p>Everything that fails throws (or completes the future with) a <code>RuneClanException</code>:</p>
{table(["Method", "Gives"], [
        ["<code>status()</code>", "The HTTP status, or 0 when no answer came back."],
        ["<code>code()</code>", "The hub's error code (below), or one of the SDK's own."],
        ["<code>getMessage()</code>", "Plain words you can log."],
        ["<code>retryAfter()</code>", "Seconds to wait, for a 429."],
        ["<code>body()</code>", "The hub's whole error answer, as JSON."],
        ["<code>local()</code>", "True when nothing was sent (the SDK stopped it)."],
        ["<code>isNotFound()</code>, <code>isRateLimited()</code>", "Shortcuts."],
    ])}
{code_block('''try {
    hub.players().events(alias, "XP", null, null, 20).complete();
} catch (RuneClanException e) {
    switch (e.code()) {
        case "missing_scope" -> log.warn("Ask for events:read on the key");
        case "not_found" -> log.info("Unknown player, or they didn't grant us");
        default -> log.error("Hub said " + e.status() + " " + e.code() + ": " + e.getMessage());
    }
}''')}
<h3>The SDK's own codes</h3>
{table(["Code", "Means"], code_rows)}
<details class="more"><summary>Every hub error code ({len(all_codes)})</summary>
<p class="muted">Errors are <code>{{"error": "&lt;code&gt;", "message": "&lt;words&gt;"}}</code>. The most common: <code>401</code> no or bad key,
<code>403 missing_scope</code>, <code>404 not_found</code> (also "not yours to see"), <code>429 rate_limited</code>, <code>426 sdk_too_old</code>.</p>
{table(["Code", "Status", "Note"], all_codes)}
</details>
"""))
    guide.append(("settings", "Settings", f"""
<p>Everything has a default; change only what you need:</p>
{code_block('''RuneClanClient hub = RuneClan.connect(RuneClanConfig.builder()
        .apiKey(System.getenv("MY_KEY_NAME"))      // or leave out: RUNECLAN_API_KEY, then runeclan.properties
        .baseUrl("http://localhost:8090")           // another hub, e.g. a local one
        .requestTimeout(Duration.ofSeconds(10))
        .maxAttempts(3)
        .maxRetryWait(Duration.ofSeconds(60))
        .executor(myExecutor)                       // runs submit()/queue(); virtual threads on Java 21+
        .userAgent("my-bot/1.4")
        .build());''')}
{table(["Where", "What"], [
        ["<code>RUNECLAN_API_KEY</code>", "The key (handy on hosts like Railway)."],
        ["<code>RUNECLAN_API_URL</code>", "The hub's address (default <code>https://api.runeclans.com</code>)."],
        ["<code>RUNECLAN_CONFIG</code>", "A key file somewhere other than <code>./runeclan.properties</code>."],
        ["Order", "What you pass in code, then the environment, then the file. The SDK never logs the key."],
    ])}
"""))
    guide.append(("testing", "Testing your bot", f"""
<p>Pass a <code>FakeTransport</code> and nothing touches the network. Answers are served in order; the first one is the handshake.</p>
{code_block('''FakeTransport fake = new FakeTransport()
        .answer(200, "{\\"appId\\":\\"a\\",\\"scopes\\":[\\"players:read\\"]}")        // the handshake
        .answer(200, "{\\"alias\\":\\"a1_x\\",\\"characters\\":[]}");
RuneClanClient hub = RuneClan.connect(RuneClanConfig.builder().apiKey(testKey).transport(fake).build());
// ... call your code, then look at fake.requests()''')}
<p><code>ApiAction.of(value)</code> makes a ready answer for your own fakes. See the <a href="#ex-TestingYourBot">TestingYourBot</a> example.</p>
"""))

    nav.append('<div class="navgroup"><div class="navhead">Guide</div>' + "".join(f'<a href="#{i}">{t}</a>' for i, t, _ in guide) +
               '<a href="#examples">Examples</a></div>')
    for i, t, body in guide:
        main.append(f'<section id="{i}" class="doc" data-search="{esc(t.lower())}"><h2>{t}</h2>{body}</section>')

    # ---- examples
    ex_parts = []
    ex_nav = []
    for f in sorted(EXAMPLES.glob("*.java")):
        src = f.read_text(encoding="utf-8")
        name = f.stem
        m = re.search(r"/\*\*(.*?)\*/\s*public class", src, re.S)
        about = javadoc_text(m.group(1)) if m else ""
        ex_nav.append(f'<a href="#ex-{name}">{name}</a>')
        ex_parts.append(f'''<details class="example" id="ex-{name}" data-search="{esc((name + " " + about).lower())}">
<summary><span class="exname">{name}</span><span class="exabout">{inline(about)}</span></summary>
<p class="muted">File <code>examples/{f.name}</code>. Run it with <code>java -cp runeclan-sdk.jar:jackson-databind.jar:jackson-core.jar:jackson-annotations.jar examples/{f.name}</code> (Java 17+, no build needed).</p>
{code_block(src)}</details>''')
    main.append(f'<section id="examples" class="doc" data-search="examples"><h2>Examples</h2><p>Whole programs, each one checked by the SDK\'s own '
                f'tests so they keep working. Click one to open it.</p>{"".join(ex_parts)}</section>')

    # ---- SDK methods
    nav.append('<div class="navgroup"><div class="navhead">SDK methods</div>' +
               "".join(f'<a href="#g-{cls}">{"hub." + a if a != "hub" else "hub"}</a>' for a, cls, _, _ in groups) + "</div>")
    for accessor, cls, blurb, merged in groups:
        cards = []
        for name, overloads in merged.items():
            key = f"{cls}.{name}"
            prefix = ("RuneClanClient." if overloads[0]["static"] else "hub.") if cls == "RuneClanClient" else \
                (f"{cls}." if overloads[0]["static"] else f"hub.{accessor}.")
            sigs = "".join(f'<div class="sig"><span class="ret">{esc(m["ret"])}</span> {esc(prefix)}<b>{esc(name)}</b>({esc(m["params"])})</div>'
                           for m in overloads)
            doc = next((m["doc"] for m in overloads if m["doc"]), "")
            rlinks = []
            for m in overloads:
                for r in m["routes"]:
                    o = const_to_op.get(r)
                    if o and r not in [x[0] for x in rlinks]:
                        rlinks.append((r, o))
            if not doc and rlinks:
                doc = rlinks[0][1]["op"].get("summary", "") + "."
            scopes = sorted({s for _, o in rlinks for s in o["scopes"]})
            meta = ""
            if rlinks:
                meta = '<div class="meta">' + scope_chips(scopes) + " " + " ".join(
                    f'<a class="routeref" href="#r-{r}">{badge(o["method"])}<code>{esc(o["path"])}</code></a>' for r, o in rlinks) + "</div>"
            ex = C.EXAMPLES.get(key, "")
            search = f"{accessor} {cls} {name} {doc}".lower()
            cards.append(f'<article class="card" id="m-{slug(cls)}-{slug(name)}" data-search="{esc(search)}">{sigs}<p>{inline(doc)}</p>{meta}'
                         f'{code_block(ex) if ex else ""}</article>')
        title = "hub" if accessor == "hub" else f"hub.{accessor}"
        main.append(f'<section id="g-{cls}" class="group" data-search="{esc((cls + " " + blurb).lower())}"><h2><code>{title}</code></h2>'
                    f'<p class="blurb">{esc(blurb)}</p>{"".join(cards)}</section>')

    # ---- routes
    tags = []
    for o in ops:
        if o["tag"] not in tags:
            tags.append(o["tag"])
    nav.append('<div class="navgroup"><div class="navhead">HTTP routes</div>' +
               "".join(f'<a href="#tag-{slug(t)}">{esc(t)} <span class="count">{sum(1 for o in ops if o["tag"] == t)}</span></a>' for t in tags) + "</div>")
    for t in tags:
        cards = []
        for o in [x for x in ops if x["tag"] == t]:
            op, info, M, path = o["op"], o["info"], o["method"], o["path"]
            desc = SCOPE_RE.sub("", op.get("description", "")).strip()
            prm_rows = []
            for p in params_of(sp, op):
                if p["name"] == "X-Guild-Id":
                    continue
                schema = p.get("schema", {})
                d = inline(p.get("description", ""))
                lim = []
                if ("minimum" in schema or "maximum" in schema) and " to " not in p.get("description", ""):
                    lim.append(f"{schema.get('minimum', '')} to {schema.get('maximum', '')}".strip())
                if schema.get("enum"):
                    lim.append("one of " + ", ".join(f"<code>{esc(str(e))}</code>" for e in schema["enum"]))
                if schema.get("default") is not None:
                    lim.append(f"default <code>{esc(str(schema['default']))}</code>")
                if lim:
                    d += (" " if d else "") + "(" + "; ".join(lim) + ")"
                where = {"path": "path", "query": "query", "header": "header"}.get(p["in"], p["in"])
                prm_rows.append([f'<code>{esc(p["name"])}</code>' + (' <span class="req">required</span>' if p.get("required") else ""),
                                 where, sp.type_label(schema), d])
            guild = any(p["name"] == "X-Guild-Id" for p in params_of(sp, op))
            parts = [f'<p>{inline(op.get("summary", ""))}.' + (f" {inline(desc)}" if desc else "") + "</p>"]
            if prm_rows:
                parts.append("<h4>Parameters</h4>" + table(["Name", "In", "Type", "What it is"], prm_rows))
            if guild:
                parts.append('<p class="muted">Takes <code>X-Guild-Id</code> (or <code>hub.forGuild(guildId)</code>): the Discord server the read '
                             'is for, needed when a player limited their grant to some servers.</p>')
            if "requestBody" in op:
                bs = next(iter(op["requestBody"].get("content", {}).values()), {}).get("schema", {})
                name = sp.ref_name(bs)
                parts.append("<h4>Body" + (f' <a class="tref" href="#t-{name}">{esc(name)}</a>' if name else "") + "</h4>" +
                             field_table(sp, sp.fields(bs)))
            code, rdesc, rschema = ok_schema(sp, op)
            rname = sp.ref_name(rschema) if rschema else None
            media = ok_media(op)
            ans = f"<h4>Answer <span class=\"muted\">{code} {inline(rdesc)}</span>" + (f' <a class="tref" href="#t-{rname}">{esc(rname)}</a>' if rname else "") + "</h4>"
            if rschema:
                ans += field_table(sp, sp.fields(rschema))
                sample = json.dumps(sp.sample(rschema), indent=2)
                if len(sample) < 6000:
                    ans += f'<details class="more"><summary>Sample answer</summary>{code_block(sample, "json")}</details>'
            elif media:
                ans += f'<p class="muted">A <code>{esc(media)}</code> file (use <code>.asBytes()</code>).</p>'
            else:
                ans += '<p class="muted">No body.</p>'
            parts.append(ans)
            errs = route_errors(sp, op, info["keyed"], o["scopes"], "{" in path)
            parts.append("<h4>Errors</h4>" + table(["Status", "Code", "When"], [[str(s), f"<code>{esc(c)}</code>", inline(w)] for s, c, w in errs]))
            friendly = by_route.get(info["const"], [])
            if friendly:
                a, cls, n = friendly[0]
                java = C.EXAMPLES.get(f"{cls}.{n}", "")
                via = f'<p class="muted">With the SDK: <a href="#m-{slug(cls)}-{slug(n)}"><code>{"hub" if a == "hub" else "hub." + a}.{esc(n)}(...)</code></a></p>'
            else:
                java = route_java(sp, M, path, op, info)
                via = '<p class="muted">No friendly method yet: <code>hub.route(...)</code> reaches it with the same scope checks, pacing and retries.</p>'
            curl = route_curl(sp, M, path, op, info["keyed"])
            parts.append(f'''<h4>Example</h4>{via}<div class="tabs"><div class="tabbar"><button type="button" class="tab on" data-t="java">Java</button><button type="button" class="tab" data-t="curl">curl</button></div>
<div class="pane on" data-t="java">{code_block(java)}</div><div class="pane" data-t="curl">{code_block(curl, "shell")}</div></div>''')
            search = f"{M} {path} {op.get('summary', '')} {info['const']} {' '.join(o['scopes'])}".lower()
            cards.append(f'''<details class="route" id="r-{info['const']}" data-search="{esc(search)}">
<summary>{badge(M)}<code class="path">{esc(path)}</code><span class="summary">{inline(op.get("summary", ""))}</span>{scope_chips(o["scopes"])}</summary>
<div class="routebody"><div class="const">Routes.{info['const']}</div>{"".join(parts)}</div></details>''')
        main.append(f'<section id="tag-{slug(t)}" class="group" data-search="{esc(t.lower())}"><h2>{esc(t)}</h2>{"".join(cards)}</section>')

    # ---- types
    used_types = sorted(n for n in sp.schemas if n in sp.models and n != "Error")
    nav.append(f'<div class="navgroup"><div class="navhead">Types</div><a href="#types">All data types <span class="count">{len(used_types)}</span></a></div>')
    tcards = []
    for n in used_types:
        s = sp.merged(sp.schemas[n])
        tcards.append(f'<details class="type" id="t-{n}" data-search="{esc(n.lower())}"><summary><code>{n}</code><span class="summary">'
                      f'{inline(s.get("description") or "")}</span></summary><div class="routebody"><p class="muted">Java: '
                      f'<code>com.runeclans.sdk.model.{n}</code> (a record; fields are its methods, e.g. <code>x.{camel(next(iter(s.get("properties") or {"field": 0})))}()</code>).</p>'
                      f'{field_table(sp, sp.fields(sp.schemas[n]))}</div></details>')
    main.append(f'<section id="types" class="group" data-search="types"><h2>Data types</h2><p class="blurb">Every answer the SDK reads into a Java record. '
                f'Fields the hub adds later are ignored, so a newer hub never breaks your bot.</p>{"".join(tcards)}</section>')

    page = TEMPLATE.replace("%%VERSION%%", esc(version)).replace("%%SOURCE%%", esc(source)).replace("%%COUNT%%", str(len(ops))) \
        .replace("%%NAV%%", "".join(nav)).replace("%%MAIN%%", "\n".join(main))
    return page, problems


TEMPLATE = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>RuneClan SDK docs</title>
<meta name="description" content="How to use the RuneClans hub API from Java with the RuneClan SDK: every method and route, with examples.">
<!-- Generated by tools/gen_docs.py from the SDK sources and spec/openapi.json. Do not edit by hand. -->
<style>
:root{--bg:#fbfaf7;--panel:#ffffff;--side:#f3f1ec;--ink:#1d1f23;--muted:#666a73;--line:#e3e0d8;--accent:#9a5b13;--accent-ink:#ffffff;
--code-bg:#f6f4ef;--tk:#8a3ffc;--ts:#2a7a3b;--tn:#b4520a;--tt:#1f5fbf;--tc:#8a8f98;--get:#1f7a4d;--put:#9a6b00;--post:#1f5fbf;--delete:#b3261e;--patch:#7a3fb3;
--chip:#efe7da;--chip-ink:#6b4410;--shadow:0 1px 2px rgba(0,0,0,.05);color-scheme:light}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#15171b;--panel:#1c1f24;--side:#181a1f;--ink:#e7e5e0;--muted:#9da1a9;--line:#2c3037;--accent:#e0a458;--accent-ink:#15171b;
--code-bg:#111317;--tk:#c69cff;--ts:#8fd19e;--tn:#f0a35e;--tt:#7fb2ff;--tc:#7d828c;--get:#5fcf96;--put:#e6c15a;--post:#7fb2ff;--delete:#ff8a80;--patch:#c69cff;
--chip:#2b2620;--chip-ink:#e6c08f;--shadow:none;color-scheme:dark}}
:root[data-theme="dark"]{--bg:#15171b;--panel:#1c1f24;--side:#181a1f;--ink:#e7e5e0;--muted:#9da1a9;--line:#2c3037;--accent:#e0a458;--accent-ink:#15171b;
--code-bg:#111317;--tk:#c69cff;--ts:#8fd19e;--tn:#f0a35e;--tt:#7fb2ff;--tc:#7d828c;--get:#5fcf96;--put:#e6c15a;--post:#7fb2ff;--delete:#ff8a80;--patch:#c69cff;
--chip:#2b2620;--chip-ink:#e6c08f;--shadow:none;color-scheme:dark}
*{box-sizing:border-box}
html{scroll-padding-top:72px}
body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.6 system-ui,-apple-system,"Segoe UI",Roboto,Ubuntu,sans-serif;-webkit-text-size-adjust:100%}
code,pre,.sig,.const{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,"Liberation Mono",monospace;font-size:.88em}
a{color:var(--accent);text-decoration:none}a:hover{text-decoration:underline}
header{position:sticky;top:0;z-index:5;display:flex;align-items:center;gap:12px;padding:10px 16px;background:var(--panel);border-bottom:1px solid var(--line)}
header .brand{font-weight:700;white-space:nowrap}header .brand span{color:var(--muted);font-weight:500;margin-left:6px}
#q{flex:1;min-width:0;max-width:520px;padding:8px 12px;border:1px solid var(--line);border-radius:8px;background:var(--bg);color:var(--ink);font:inherit}
#q:focus{outline:2px solid var(--accent);outline-offset:1px}
.iconbtn{border:1px solid var(--line);background:var(--bg);color:var(--ink);border-radius:8px;padding:7px 10px;font:inherit;cursor:pointer}
#menu{display:none}
.layout{display:grid;grid-template-columns:260px minmax(0,1fr);max-width:1400px;margin:0 auto}
nav{position:sticky;top:57px;align-self:start;height:calc(100vh - 57px);overflow:auto;padding:16px 10px 40px;background:var(--side);border-right:1px solid var(--line)}
.navgroup{margin-bottom:18px}.navhead{font-size:.75rem;text-transform:uppercase;letter-spacing:.06em;color:var(--muted);margin:0 8px 4px}
nav a{display:flex;justify-content:space-between;padding:4px 8px;border-radius:6px;color:var(--ink)}nav a:hover{background:var(--line);text-decoration:none}
.count{color:var(--muted);font-size:.8rem}
main{padding:24px 32px 80px;min-width:0}
section{margin-bottom:44px}
h2{font-size:1.45rem;margin:0 0 10px;padding-top:6px}h3{font-size:1.05rem;margin:22px 0 8px}h4{font-size:.95rem;margin:18px 0 6px}
.lead{font-size:1.08rem}.blurb{color:var(--muted);margin-top:-4px}.muted{color:var(--muted)}
.steps{padding-left:20px}.steps li{margin-bottom:14px}
.code{position:relative;margin:10px 0}
pre{margin:0;padding:12px 14px;background:var(--code-bg);border:1px solid var(--line);border-radius:8px;overflow:auto;line-height:1.5}
.copy{position:absolute;top:6px;right:6px;border:1px solid var(--line);background:var(--panel);color:var(--muted);border-radius:6px;font:12px system-ui,sans-serif;padding:3px 8px;cursor:pointer;opacity:.85}
.copy:hover{opacity:1;color:var(--ink)}
.tk{color:var(--tk)}.ts{color:var(--ts)}.tn{color:var(--tn)}.tt{color:var(--tt)}.tc{color:var(--tc);font-style:italic}
p code,td code,li code,summary code,h4 code{background:var(--code-bg);border:1px solid var(--line);border-radius:4px;padding:0 4px}
pre code,li pre code{background:none;border:0;padding:0;font-size:1em}
td a,p code,td code,li code{overflow-wrap:anywhere}
td:first-child code{white-space:nowrap;overflow-wrap:normal}
.tablewrap{overflow-x:auto;margin:8px 0}
table{border-collapse:collapse;width:100%;font-size:.92em}th,td{text-align:left;vertical-align:top;padding:7px 10px;border-bottom:1px solid var(--line)}
th{font-size:.78rem;text-transform:uppercase;letter-spacing:.04em;color:var(--muted);font-weight:600}
.req{font-size:.72rem;color:var(--delete);margin-left:4px}
.card{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:14px 16px;margin:12px 0;box-shadow:var(--shadow)}
.card p{margin:6px 0}.sig{font-size:.92rem;overflow-wrap:anywhere}.sig .ret{color:var(--muted)}.sig b{color:var(--accent)}
.meta{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin:6px 0}
.routeref{display:inline-flex;gap:6px;align-items:center;color:var(--ink)}
.chip{display:inline-block;font-size:.75rem;padding:1px 8px;border-radius:999px;background:var(--chip);color:var(--chip-ink);white-space:nowrap}
.chip.any{background:transparent;border:1px solid var(--line);color:var(--muted)}
.verb{display:inline-block;min-width:58px;text-align:center;font:700 .72rem/1.9 ui-monospace,monospace;border-radius:5px;color:#fff;padding:0 6px}
.v-get{background:var(--get)}.v-put{background:var(--put)}.v-post{background:var(--post)}.v-delete{background:var(--delete)}.v-patch{background:var(--patch)}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]) .verb{color:#15171b}}:root[data-theme="dark"] .verb{color:#15171b}
details.route,details.type,details.example{background:var(--panel);border:1px solid var(--line);border-radius:10px;margin:8px 0;box-shadow:var(--shadow)}
details>summary{cursor:pointer;list-style:none;display:flex;flex-wrap:wrap;gap:10px;align-items:center;padding:10px 14px}
details>summary::-webkit-details-marker{display:none}
details>summary::before{content:"";width:7px;height:7px;border-right:2px solid var(--muted);border-bottom:2px solid var(--muted);transform:rotate(-45deg);transition:transform .15s;flex:none}
details[open]>summary::before{transform:rotate(45deg)}
summary .path{font-weight:600;overflow-wrap:anywhere;background:none;border:0;padding:0}summary .summary{color:var(--muted);flex:1 1 200px;min-width:0}
.routebody{padding:0 16px 14px;border-top:1px solid var(--line)}
.const{color:var(--muted);margin-top:10px}
.exname{font-weight:700}.exabout{color:var(--muted);flex:1 1 200px;min-width:0;overflow-wrap:anywhere}
details.more{margin:10px 0}details.more>summary{padding:4px 0;color:var(--accent)}
.tabbar{display:flex;gap:4px;margin-top:6px}.tab{border:1px solid var(--line);background:var(--bg);color:var(--muted);border-radius:6px 6px 0 0;padding:4px 12px;font:inherit;font-size:.85rem;cursor:pointer}
.tab.on{background:var(--code-bg);color:var(--ink);border-bottom-color:var(--code-bg)}.pane{display:none}.pane.on{display:block}.pane .code{margin-top:-1px}
.hidden{display:none !important}
#empty{display:none;padding:40px 0;color:var(--muted)}
footer{color:var(--muted);font-size:.85rem;border-top:1px solid var(--line);padding-top:16px}
@media (max-width:860px){
  .layout{grid-template-columns:minmax(0,1fr)}
  nav{display:none;position:fixed;top:57px;left:0;right:0;bottom:0;height:auto;z-index:4;border-right:0}
  body.navopen nav{display:block}
  #menu{display:inline-block}
  main{padding:16px 16px 60px}
  header .brand span{display:none}
}
@media (prefers-reduced-motion:reduce){*{transition:none !important}}
</style>
</head>
<body>
<header>
<button class="iconbtn" id="menu" type="button" aria-label="Menu">☰</button>
<div class="brand">RuneClan SDK<span>v%%VERSION%% · Java</span></div>
<input id="q" type="search" placeholder="Search methods, routes, types…  ( / )" aria-label="Search" autocomplete="off">
<button class="iconbtn" id="theme" type="button" aria-label="Switch light or dark">Theme</button>
</header>
<div class="layout">
<nav id="nav">%%NAV%%</nav>
<main id="main">
%%MAIN%%
<p id="empty">Nothing matches. Try a path like <code>/v1/clans</code>, a method like <code>byName</code> or a scope like <code>events:read</code>.</p>
<footer>RuneClan SDK v%%VERSION%% for Java · %%COUNT%% public hub routes · made from the hub's API description (commit %%SOURCE%%) by
<code>tools/gen_docs.py</code>. MIT licence.</footer>
</main>
</div>
<script>
(function(){
  var root=document.documentElement;
  try{var t=localStorage.getItem("rc-docs-theme");if(t)root.setAttribute("data-theme",t);}catch(e){}
  document.getElementById("theme").addEventListener("click",function(){
    var dark=root.getAttribute("data-theme")?root.getAttribute("data-theme")==="dark":matchMedia("(prefers-color-scheme: dark)").matches;
    var next=dark?"light":"dark";root.setAttribute("data-theme",next);try{localStorage.setItem("rc-docs-theme",next);}catch(e){}
  });
  document.getElementById("menu").addEventListener("click",function(){document.body.classList.toggle("navopen");});
  document.getElementById("nav").addEventListener("click",function(e){if(e.target.closest("a"))document.body.classList.remove("navopen");});
  document.addEventListener("click",function(e){
    var b=e.target.closest(".copy");
    if(b){var text=b.parentNode.querySelector("pre").innerText;
      var done=function(){b.textContent="Copied";setTimeout(function(){b.textContent="Copy";},1200);};
      if(navigator.clipboard&&window.isSecureContext){navigator.clipboard.writeText(text).then(done,function(){fallback(text);done();});}else{fallback(text);done();}
      return;}
    var tab=e.target.closest(".tab");
    if(tab){var box=tab.closest(".tabs"),t=tab.getAttribute("data-t");
      box.querySelectorAll(".tab,.pane").forEach(function(x){x.classList.toggle("on",x.getAttribute("data-t")===t);});}
  });
  function fallback(text){var a=document.createElement("textarea");a.value=text;a.style.position="fixed";a.style.opacity="0";document.body.appendChild(a);a.select();try{document.execCommand("copy");}catch(e){}a.remove();}
  function openTarget(){var id=decodeURIComponent(location.hash.slice(1));if(!id)return;var el=document.getElementById(id);
    if(el&&el.tagName==="DETAILS"){el.open=true;el.scrollIntoView();}}
  window.addEventListener("hashchange",openTarget);openTarget();
  var q=document.getElementById("q"),items=[].slice.call(document.querySelectorAll("[data-search]")),timer;
  function filter(){
    var words=q.value.toLowerCase().trim().split(/\s+/).filter(Boolean),any=false;
    document.querySelectorAll("section").forEach(function(sec){
      var secHit=words.length===0,kids=sec.querySelectorAll(".card,details.route,details.type,details.example");
      var secText=sec.getAttribute("data-search")||"",hits=0;
      kids.forEach(function(k){var s=(k.getAttribute("data-search")||"")+" "+secText;
        var ok=words.every(function(w){return s.indexOf(w)>=0;});k.classList.toggle("hidden",!ok&&words.length>0);if(ok)hits++;
        if(k.tagName==="DETAILS"&&words.length>0&&ok&&kids.length<4)k.open=true;});
      var selfHit=kids.length===0&&words.every(function(w){return (sec.textContent.toLowerCase()).indexOf(w)>=0;});
      var show=secHit||hits>0||selfHit;sec.classList.toggle("hidden",!show);if(show)any=true;});
    document.getElementById("empty").style.display=any?"none":"block";
  }
  q.addEventListener("input",function(){clearTimeout(timer);timer=setTimeout(filter,80);});
  document.addEventListener("keydown",function(e){if(e.key==="/"&&document.activeElement!==q&&!/INPUT|TEXTAREA/.test(document.activeElement.tagName)){e.preventDefault();q.focus();}
    if(e.key==="Escape"&&document.activeElement===q){q.value="";filter();q.blur();}});
})();
</script>
</body>
</html>
"""


def main():
    page, problems = build()
    if problems:
        print("gen_docs.py: fix these first:\n  " + "\n  ".join(problems), file=sys.stderr)
        return 1
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != page:
            print("docs/index.html is out of date: run python3 tools/gen_docs.py", file=sys.stderr)
            return 1
        print(f"docs/index.html is up to date ({len(page) // 1024} KB)")
        return 0
    OUT.parent.mkdir(exist_ok=True)
    OUT.write_text(page, encoding="utf-8", newline="\n")
    print(f"wrote {OUT.relative_to(ROOT)} ({len(page) // 1024} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
