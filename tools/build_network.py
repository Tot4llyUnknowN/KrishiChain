#!/usr/bin/env python3
"""Builds app/src/main/assets/network.json for KrishiChain.

Usage:
    python build_network.py                 (uses prices.csv)
    python build_network.py --demo-prices   (DUMMY prices, for coding only)

Run once. Geocoding and OSRM answers are cached in tools/*.json.
"""
import csv
import json
import math
import os
import random
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "network.json")

# Nominatim requires an identifying User-Agent. Put your real email here.
USER_AGENT = "KrishiChain-KUET-student-project (youractualname@gmail.com)"

# ------------------------- ASSUMPTIONS (replace or cite!) -------------------------
RATE_BDT_PER_TON_KM = 4.0      # ASSUMPTION: truck freight cost per ton per km
CREDIT_BDT_PER_TON_KM = 5.0    # ASSUMPTION: backhaul revenue per ton-km for return cargo
BACKLOAD_ORIGINS = {"Dhaka", "Narayanganj", "Gazipur", "Chattogram"}
#   A truck leaving these import/consumer hubs toward a farming area can load goods for the
#   return trip, so those arcs get a backhaul credit (may exceed the arc's cost -> negative).
CAPACITY_TONS_PER_DAY = {"village": 25, "hub": 60, "market": 100}   # ASSUMPTION
#   An arc's capacity = the smaller of its two end types.
TOLL_BDT_PER_TON = {}          # e.g. {("Khulna", "Jessore"): 12.0}  fill with real values
K_NEAREST = 4                  # each node connects to its 4 nearest by road
# -----------------------------------------------------------------------------------

BD_LAT = (20.5, 26.7)
BD_LON = (88.0, 92.8)
CROPS = ["potato", "tomato", "onion"]


def http_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as err:
        body = err.read().decode("utf-8", errors="replace")[:300]
        sys.exit(f"HTTP {err.code} from {url[:60]}...\nServer said: {body}")


def load_cache(name):
    path = os.path.join(HERE, name)
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    return {}


def save_cache(name, data):
    with open(os.path.join(HERE, name), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)


def load_hubs():
    hubs = []
    with open(os.path.join(HERE, "hubs.csv"), encoding="utf-8") as f:
        for row in csv.DictReader(f):
            hubs.append(row)
    return hubs


def geocode_all(hubs):
    cache = load_cache("coords_cache.json")
    print("\n=== Geocoding (verify every line!) ===")
    for h in hubs:
        if h["lat"].strip() and h["lon"].strip():
            h["lat"], h["lon"] = float(h["lat"]), float(h["lon"])
            print(f"{h['name']:<13} MANUAL  {h['lat']:.4f}, {h['lon']:.4f}")
            continue
        q = h["query"]
        if q not in cache:
            url = ("https://nominatim.openstreetmap.org/search?format=json&limit=1"
                   "&countrycodes=bd&accept-language=en&q=" + urllib.parse.quote(q))
            res = http_json(url)
            time.sleep(1.1)  # Nominatim policy: max 1 request per second
            if not res:
                sys.exit(f"Could not geocode '{q}'. Put lat/lon in hubs.csv and rerun.")
            cache[q] = {"lat": float(res[0]["lat"]), "lon": float(res[0]["lon"]),
                        "display": res[0]["display_name"]}
            save_cache("coords_cache.json", cache)
        c = cache[q]
        h["lat"], h["lon"] = c["lat"], c["lon"]
        ok = BD_LAT[0] <= c["lat"] <= BD_LAT[1] and BD_LON[0] <= c["lon"] <= BD_LON[1]
        flag = "" if ok else "   <-- OUTSIDE BANGLADESH, FIX IT"
        print(f"{h['name']:<13} {c['lat']:.4f}, {c['lon']:.4f}  {c['display'][:70]}{flag}")
        if not ok:
            sys.exit("Fix the coordinates above, then rerun.")


def osrm_table(hubs):
    cache = load_cache("osrm_cache.json")
    key = ";".join(f"{h['lon']:.5f},{h['lat']:.5f}" for h in hubs)
    if cache.get("key") != key:
        print("\nCalling OSRM once for the full distance table...")
        url = ("https://router.project-osrm.org/table/v1/driving/" + key +
               "?annotations=duration,distance")
        data = http_json(url)
        if data.get("code") != "Ok":
            sys.exit("OSRM error: " + str(data.get("code")) + " " + str(data.get("message")))
        cache = {"key": key, "durations": data["durations"], "distances": data["distances"]}
        save_cache("osrm_cache.json", cache)
    else:
        print("\nUsing cached OSRM table.")
    return cache["durations"], cache["distances"]


def haversine_km(a, b):
    r = 6371.0
    p1, p2 = math.radians(a["lat"]), math.radians(b["lat"])
    dp = p2 - p1
    dl = math.radians(b["lon"] - a["lon"])
    x = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(x))


def choose_pairs(n, dist):
    """K-nearest (by road km) union a minimum spanning tree, so the graph is connected."""
    pairs = set()
    for i in range(n):
        order = sorted((j for j in range(n) if j != i and dist[i][j] is not None),
                       key=lambda j: dist[i][j])
        for j in order[:K_NEAREST]:
            pairs.add((min(i, j), max(i, j)))
    parent = list(range(n))

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    all_pairs = sorted(((dist[i][j], i, j) for i in range(n) for j in range(i + 1, n)
                        if dist[i][j] is not None))
    for _, i, j in all_pairs:
        a, b = find(i), find(j)
        if a != b:
            parent[a] = b
            pairs.add((i, j))
    return sorted(pairs)


def build(hubs, dur, dist_m):
    n = len(hubs)
    km = [[None if dist_m[i][j] is None else dist_m[i][j] / 1000.0 for j in range(n)]
          for i in range(n)]
    pairs = choose_pairs(n, km)
    edges = []
    for (i, j) in pairs:
        for (u, v) in ((i, j), (j, i)):
            d = round(km[u][v], 1)
            minutes = round(dur[u][v] / 60.0)
            cost = round(d * RATE_BDT_PER_TON_KM, 1)
            credit = (round(d * CREDIT_BDT_PER_TON_KM, 1)
                      if hubs[u]["name"] in BACKLOAD_ORIGINS
                      and hubs[v]["name"] not in BACKLOAD_ORIGINS else 0.0)
            toll = TOLL_BDT_PER_TON.get((hubs[u]["name"], hubs[v]["name"]), 0.0)
            cap = min(CAPACITY_TONS_PER_DAY[hubs[u]["type"]], CAPACITY_TONS_PER_DAY[hubs[v]["type"]])
            edges.append({"from": u + 1, "to": v + 1, "km": d, "minutes": minutes,
                          "cost": cost, "credit": credit,
                          "netCost": round(cost + toll - credit, 1),
                          "toll": toll, "capacity": cap})
    return edges


def validate(hubs, edges):
    n = len(hubs)
    print("\n=== Validation ===")
    adj = {i: set() for i in range(1, n + 1)}
    for e in edges:
        adj[e["from"]].add(e["to"])
    seen, stack = {1}, [1]
    while stack:
        u = stack.pop()
        for v in adj[u]:
            if v not in seen:
                seen.add(v)
                stack.append(v)
    print(f"Connected: {len(seen) == n} ({len(seen)}/{n} nodes reachable from node 1)")
    net = {(e["from"], e["to"]): e["netCost"] for e in edges}
    bad = [(u, v) for (u, v), w in net.items() if (v, u) in net and w + net[(v, u)] <= 0]
    print(f"Negative 2-cycles: {len(bad)} (must be 0)")
    if bad:
        sys.exit("Lower CREDIT_BDT_PER_TON_KM: it makes a negative cycle.")
    for e in edges:
        if e["from"] < e["to"]:
            a, b = hubs[e["from"] - 1], hubs[e["to"] - 1]
            ratio = e["km"] / max(haversine_km(a, b), 0.1)
            if ratio < 1.0 or ratio > 2.5:
                print(f"  CHECK {a['name']} - {b['name']}: road {e['km']} km, "
                      f"straight {haversine_km(a, b):.1f} km (ratio {ratio:.2f})")
    negs = sum(1 for e in edges if e["netCost"] < 0)
    print(f"Arcs: {len(edges)}  |  arcs with negative netCost: {negs}")
    if negs == 0:
        print("  WARNING: no negative arcs. Bellman-Ford and Johnson need some. "
              "Raise the credit rate or add backload origins.")


def make_prices(hubs, demo):
    prices = []
    ids = {h["name"]: i + 1 for i, h in enumerate(hubs)}
    path = os.path.join(HERE, "prices.csv")
    if demo:
        print("\n*** DEMO PRICES: fake numbers labeled DUMMY. Replace with DAM data before the demo. ***")
        rnd = random.Random(1)
        base = {"potato": 30, "tomato": 45, "onion": 55}
        for h in hubs:
            if h["type"] in ("market", "hub"):
                for crop in CROPS:
                    prices.append({"node": ids[h["name"]], "crop": crop,
                                   "price": round(base[crop] * rnd.uniform(0.85, 1.25), 1),
                                   "source": "DUMMY", "date": "none"})
        return prices
    if not os.path.exists(path):
        print("\nNo prices.csv found: 'prices' will be empty.")
        return prices
    with open(path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if not row["price_bdt_per_kg"].strip():
                continue
            if row["node"] not in ids:
                sys.exit("prices.csv: unknown node " + row["node"])
            prices.append({"node": ids[row["node"]], "crop": row["crop"].strip().lower(),
                           "price": float(row["price_bdt_per_kg"]),
                           "source": row["source"].strip(), "date": row["date"].strip()})
    print(f"\nLoaded {len(prices)} real prices from prices.csv")
    return prices


def main():
    demo = "--demo-prices" in sys.argv
    hubs = load_hubs()
    geocode_all(hubs)
    dur, dist_m = osrm_table(hubs)
    edges = build(hubs, dur, dist_m)
    validate(hubs, edges)
    nodes = [{"id": i + 1, "name": h["name"], "type": h["type"],
              "lat": round(h["lat"], 5), "lon": round(h["lon"], 5)} for i, h in enumerate(hubs)]
    out = {
        "meta": {
            "distanceSource": "OSRM public demo server (OpenStreetMap data), fetched once",
            "assumptions": {"rateBdtPerTonKm": RATE_BDT_PER_TON_KM,
                            "creditBdtPerTonKm": CREDIT_BDT_PER_TON_KM,
                            "backloadOrigins": sorted(BACKLOAD_ORIGINS),
                            "capacityTonsPerDay": CAPACITY_TONS_PER_DAY},
            "demoPrices": demo,
        },
        "nodes": nodes, "edges": edges, "prices": make_prices(hubs, demo),
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(f"\nWrote {os.path.normpath(OUT)}")


if __name__ == "__main__":
    main()