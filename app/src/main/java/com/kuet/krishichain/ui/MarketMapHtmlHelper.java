package com.kuet.krishichain.ui;

import com.kuet.krishichain.data.EdgeEntity;
import com.kuet.krishichain.data.NodeEntity;

import java.util.List;
import java.util.Locale;

public final class MarketMapHtmlHelper {
    private MarketMapHtmlHelper() {}

    public static String buildMapHtml(List<NodeEntity> allNodes,
                                      List<EdgeEntity> allEdges,
                                      int originId,
                                      int selectedDestId,
                                      List<NodeEntity> routeNodes) {
        StringBuilder nodesJson = new StringBuilder("[");
        for (int i = 0; i < allNodes.size(); i++) {
            NodeEntity n = allNodes.get(i);
            if (i > 0) nodesJson.append(",");
            nodesJson.append(String.format(Locale.US,
                    "{\"id\":%d,\"name\":\"%s\",\"lat\":%.5f,\"lon\":%.5f}",
                    n.id, escapeJs(n.name), n.lat, n.lon));
        }
        nodesJson.append("]");

        StringBuilder edgesJson = new StringBuilder("[");
        for (int i = 0; i < allEdges.size(); i++) {
            EdgeEntity e = allEdges.get(i);
            if (e.fromId < e.toId) { // Undirected road leg
                if (edgesJson.length() > 1) edgesJson.append(",");
                edgesJson.append(String.format(Locale.US,
                        "{\"from\":%d,\"to\":%d,\"km\":%.1f}",
                        e.fromId, e.toId, e.km));
            }
        }
        edgesJson.append("]");

        StringBuilder routeJson = new StringBuilder("[");
        if (routeNodes != null && selectedDestId > 0) {
            for (int i = 0; i < routeNodes.size(); i++) {
                NodeEntity n = routeNodes.get(i);
                if (i > 0) routeJson.append(",");
                routeJson.append(String.format(Locale.US,
                        "[%.5f,%.5f]", n.lat, n.lon));
            }
        }
        routeJson.append("]");

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <meta charset='utf-8'/>\n" +
                "  <meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'/>\n" +
                "  <meta name='referrer' content='origin'/>\n" +
                "  <link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>\n" +
                "  <script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>\n" +
                "  <style>\n" +
                "    html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #F6FAF6; }\n" +
                "    .hub-pin { background: #FFFFFF; border: 2.5px solid #1B7A2F; border-radius: 50%; box-shadow: 0 2px 6px rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; cursor: pointer; }\n" +
                "    .origin-pin { background: #1B7A2F; color: white; border: 2px solid white; border-radius: 50%; display: flex; align-items: center; justify-content: center; box-shadow: 0 3px 8px rgba(0,0,0,0.35); font-size: 13px; }\n" +
                "    .dest-pin { background: #E11D48; color: white; border: 2px solid white; border-radius: 50%; display: flex; align-items: center; justify-content: center; box-shadow: 0 3px 8px rgba(0,0,0,0.35); font-size: 13px; }\n" +
                "    .osm-badge { position: absolute; bottom: 6px; right: 8px; z-index: 1000; font-size: 9px; color: #475569; background: rgba(255,255,255,0.85); padding: 2px 6px; border-radius: 4px; pointer-events: none; border: 1px solid #CBD5E1; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div id='map'></div>\n" +
                "<div class='osm-badge'>OpenStreetMap 64 Zillas</div>\n" +
                "<script>\n" +
                "  var map = L.map('map', { zoomControl: false, attributionControl: false }).setView([23.8, 90.3], 7);\n" +
                "  L.control.zoom({ position: 'topright' }).addTo(map);\n" +
                "\n" +
                "  L.tileLayer('https://{s}.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png', {\n" +
                "    maxZoom: 19,\n" +
                "    subdomains: 'abc'\n" +
                "  }).addTo(map);\n" +
                "\n" +
                "  var nodes = " + nodesJson.toString() + ";\n" +
                "  var edges = " + edgesJson.toString() + ";\n" +
                "  var originId = " + originId + ";\n" +
                "  var destId = " + selectedDestId + ";\n" +
                "  var markers = {};\n" +
                "  var routeLine = null;\n" +
                "\n" +
                "  var nodeMap = {};\n" +
                "  nodes.forEach(function(n) { nodeMap[n.id] = n; });\n" +
                "\n" +
                "  // Draw highway network edges\n" +
                "  edges.forEach(function(e) {\n" +
                "    var u = nodeMap[e.from];\n" +
                "    var v = nodeMap[e.to];\n" +
                "    if (u && v) {\n" +
                "      L.polyline([[u.lat, u.lon], [v.lat, v.lon]], {\n" +
                "        color: '#94A3B8',\n" +
                "        weight: 2,\n" +
                "        opacity: 0.65\n" +
                "      }).addTo(map);\n" +
                "    }\n" +
                "  });\n" +
                "\n" +
                "  function getIcon(nId) {\n" +
                "    if (nId === originId) {\n" +
                "      return L.divIcon({\n" +
                "        className: 'origin-pin',\n" +
                "        html: '📍',\n" +
                "        iconSize: [28, 28],\n" +
                "        iconAnchor: [14, 14]\n" +
                "      });\n" +
                "    } else if (destId > 0 && nId === destId) {\n" +
                "      return L.divIcon({\n" +
                "        className: 'dest-pin',\n" +
                "        html: '🏁',\n" +
                "        iconSize: [28, 28],\n" +
                "        iconAnchor: [14, 14]\n" +
                "      });\n" +
                "    } else {\n" +
                "      return L.divIcon({\n" +
                "        className: 'hub-pin',\n" +
                "        html: '<div style=\"width:8px;height:8px;background:#1B7A2F;border-radius:50%;\"></div>',\n" +
                "        iconSize: [22, 22],\n" +
                "        iconAnchor: [11, 11]\n" +
                "      });\n" +
                "    }\n" +
                "  }\n" +
                "\n" +
                "  function refreshMarkerIcons() {\n" +
                "    for (var id in markers) {\n" +
                "      var nId = parseInt(id, 10);\n" +
                "      markers[id].setIcon(getIcon(nId));\n" +
                "      if (nId === originId || (destId > 0 && nId === destId)) {\n" +
                "        markers[id].setZIndexOffset(1000);\n" +
                "      } else {\n" +
                "        markers[id].setZIndexOffset(0);\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "\n" +
                "  // Draw 64 Zilla Market Hub Markers\n" +
                "  nodes.forEach(function(n) {\n" +
                "    var marker = L.marker([n.lat, n.lon], { icon: getIcon(n.id) }).addTo(map);\n" +
                "    marker.on('click', function() {\n" +
                "      selectHub(n.id, n.name);\n" +
                "    });\n" +
                "    markers[n.id] = marker;\n" +
                "  });\n" +
                "\n" +
                "  window.updateRoute = function(newOriginId, newDestId, coords) {\n" +
                "    originId = newOriginId;\n" +
                "    destId = newDestId;\n" +
                "    refreshMarkerIcons();\n" +
                "    if (routeLine) {\n" +
                "      map.removeLayer(routeLine);\n" +
                "      routeLine = null;\n" +
                "    }\n" +
                "    if (coords && coords.length > 1) {\n" +
                "      routeLine = L.polyline(coords, {\n" +
                "        color: '#1B7A2F',\n" +
                "        weight: 6,\n" +
                "        opacity: 0.95,\n" +
                "        lineCap: 'round',\n" +
                "        lineJoin: 'round'\n" +
                "      }).addTo(map);\n" +
                "    }\n" +
                "    // Fixed map view: No automatic zoom in or zoom out\n" +
                "  };\n" +
                "\n" +
                "  window.clearRoute = function(newOriginId) {\n" +
                "    originId = newOriginId;\n" +
                "    destId = -1;\n" +
                "    refreshMarkerIcons();\n" +
                "    if (routeLine) {\n" +
                "      map.removeLayer(routeLine);\n" +
                "      routeLine = null;\n" +
                "    }\n" +
                "  };\n" +
                "\n" +
                "  function selectHub(id, name) {\n" +
                "    if (window.Android && window.Android.onHubSelected) {\n" +
                "      window.Android.onHubSelected(id, name);\n" +
                "    }\n" +
                "  }\n" +
                "</script>\n" +
                "</body>\n" +
                "</html>";
    }

    public static String buildCoordsJson(List<NodeEntity> routeNodes) {
        if (routeNodes == null || routeNodes.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < routeNodes.size(); i++) {
            NodeEntity n = routeNodes.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(Locale.US, "[%.5f,%.5f]", n.lat, n.lon));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escapeJs(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", " ");
    }
}
