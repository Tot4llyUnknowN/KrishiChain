package com.kuet.krishichain.ui;

import com.kuet.krishichain.data.NodeEntity;

import java.util.List;
import java.util.Locale;

public final class OsmMapHelper {
    private OsmMapHelper() {}

    public static String buildHtml(List<NodeEntity> nodes, String originName, String destName, double destPrice) {
        if (nodes == null || nodes.isEmpty()) {
            return "<html><body style='margin:0;background:#F6FAF6;display:flex;align-items:center;justify-content:center;height:100%;font-family:sans-serif;color:#52705D;'>No route points available</body></html>";
        }

        StringBuilder latlngsBuilder = new StringBuilder();
        StringBuilder namesBuilder = new StringBuilder();
        for (int i = 0; i < nodes.size(); i++) {
            NodeEntity n = nodes.get(i);
            if (i > 0) {
                latlngsBuilder.append(", ");
                namesBuilder.append(", ");
            }
            latlngsBuilder.append(String.format(Locale.US, "[%.5f, %.5f]", n.lat, n.lon));
            namesBuilder.append("\"").append(escapeJs(n.name)).append("\"");
        }

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <meta charset='utf-8' />\n" +
                "  <meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' />\n" +
                "  <meta name='referrer' content='origin' />\n" +
                "  <link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css' />\n" +
                "  <script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>\n" +
                "  <style>\n" +
                "    html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #F6FAF6; }\n" +
                "    .leaflet-popup-content-wrapper { border-radius: 12px; font-family: -apple-system, Roboto, sans-serif; box-shadow: 0 4px 12px rgba(0,0,0,0.15); }\n" +
                "    .leaflet-popup-content { margin: 10px 14px; font-size: 12px; line-height: 1.4; color: #142C1B; }\n" +
                "    .osm-attr { position: absolute; bottom: 4px; right: 6px; z-index: 1000; font-size: 9.5px; color: #475569; background: rgba(255,255,255,0.85); padding: 2px 6px; border-radius: 4px; pointer-events: none; border: 1px solid #CBD5E1; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div id='map'></div>\n" +
                "<div class='osm-attr'>OpenStreetMap</div>\n" +
                "<script>\n" +
                "  try {\n" +
                "    var map = L.map('map', { zoomControl: true, attributionControl: false });\n" +
                "\n" +
                "    // OpenStreetMap tiles via OSM France CDN (no watermarks, no 403 access blocked)\n" +
                "    L.tileLayer('https://{s}.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png', {\n" +
                "      maxZoom: 19,\n" +
                "      subdomains: 'abc',\n" +
                "      attribution: '© OpenStreetMap contributors'\n" +
                "    }).addTo(map);\n" +
                "\n" +
                "    var latlngs = [" + latlngsBuilder.toString() + "];\n" +
                "    var names = [" + namesBuilder.toString() + "];\n" +
                "\n" +
                "    // Route Polyline in KrishiChain Green\n" +
                "    var polyline = L.polyline(latlngs, {\n" +
                "      color: '#1B7A2F',\n" +
                "      weight: 5,\n" +
                "      opacity: 0.95,\n" +
                "      lineCap: 'round',\n" +
                "      lineJoin: 'round'\n" +
                "    }).addTo(map);\n" +
                "\n" +
                "    // Origin Pin\n" +
                "    var originIcon = L.divIcon({\n" +
                "      className: '',\n" +
                "      html: '<div style=\"background:#1B7A2F;color:white;width:30px;height:30px;border-radius:50%;border:2.5px solid white;display:flex;align-items:center;justify-content:center;box-shadow:0 3px 8px rgba(0,0,0,0.3);font-size:15px;\">🚜</div>',\n" +
                "      iconSize: [30, 30],\n" +
                "      iconAnchor: [15, 15]\n" +
                "    });\n" +
                "    L.marker(latlngs[0], {icon: originIcon}).addTo(map)\n" +
                "      .bindPopup('<b>📍 Origin (Farmer)</b><br>" + escapeJs(originName) + "').openPopup();\n" +
                "\n" +
                "    // Intermediate Waypoint markers\n" +
                "    for (var i = 1; i < latlngs.length - 1; i++) {\n" +
                "      var hubIcon = L.divIcon({\n" +
                "        className: '',\n" +
                "        html: '<div style=\"background:#FFFFFF;border:3px solid #1B7A2F;width:14px;height:14px;border-radius:50%;box-shadow:0 1px 4px rgba(0,0,0,0.25);\"></div>',\n" +
                "        iconSize: [14, 14],\n" +
                "        iconAnchor: [7, 7]\n" +
                "      });\n" +
                "      L.marker(latlngs[i], {icon: hubIcon}).addTo(map)\n" +
                "        .bindPopup('<b>Waypoint ' + i + ':</b> ' + names[i]);\n" +
                "    }\n" +
                "\n" +
                "    // Destination Market Pin\n" +
                "    var destIcon = L.divIcon({\n" +
                "      className: '',\n" +
                "      html: '<div style=\"background:#F59E0B;color:white;width:30px;height:30px;border-radius:50%;border:2.5px solid white;display:flex;align-items:center;justify-content:center;box-shadow:0 3px 8px rgba(0,0,0,0.3);font-size:15px;\">🏁</div>',\n" +
                "      iconSize: [30, 30],\n" +
                "      iconAnchor: [15, 15]\n" +
                "    });\n" +
                "    var priceText = " + (destPrice > 0 ? "'<br>Price: ৳" + destPrice + "/kg'" : "''") + ";\n" +
                "    L.marker(latlngs[latlngs.length - 1], {icon: destIcon}).addTo(map)\n" +
                "      .bindPopup('<b>🎯 Destination Market</b><br>" + escapeJs(destName) + "' + priceText);\n" +
                "\n" +
                "    map.fitBounds(polyline.getBounds(), { padding: [35, 35] });\n" +
                "  } catch(e) {\n" +
                "    document.body.innerHTML = '<div style=\"padding:20px;font-family:sans-serif;color:#1B7A2F;text-align:center;\"><h3>Route: ' + names[0] + ' → ' + names[names.length-1] + '</h3><p>Connecting ' + names.length + ' cities/waypoints across OpenStreetMap.</p></div>';\n" +
                "  }\n" +
                "</script>\n" +
                "</body>\n" +
                "</html>";
    }

    private static String escapeJs(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", " ");
    }
}
