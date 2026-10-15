package com.kuet.krishichain.data;

import android.content.Context;
import android.content.res.XmlResourceParser;

import com.kuet.krishichain.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Reads res/xml/crops.xml with the pull parser. */
public final class CropXmlParser {
    private CropXmlParser() { }

    public static List<CropInfo> parse(Context context)
            throws XmlPullParserException, IOException {
        List<CropInfo> crops = new ArrayList<>();
        XmlResourceParser p = context.getResources().getXml(R.xml.crops);
        try {
            int event = p.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "crop".equals(p.getName())) {
                    String key = p.getAttributeValue(null, "key");
                    double rate = Double.parseDouble(
                            p.getAttributeValue(null, "spoilagePctPerHour"));
                    crops.add(new CropInfo(key, rate));
                }
                event = p.next();
            }
        } finally {
            p.close();
        }
        return crops;
    }
}