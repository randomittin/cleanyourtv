package com.gathin.iconpack.minimal;

import android.app.Activity;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/** Answers the icon-pack intents launchers send, and says how many icons the pack maps. */
public class PackInfoActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Toast.makeText(this, getString(R.string.pack_info, countIcons()), Toast.LENGTH_SHORT).show();
        finish();
    }

    private int countIcons() {
        int count = 0;
        try (XmlResourceParser parser = getResources().getXml(R.xml.appfilter)) {
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event == XmlPullParser.START_TAG && "item".equals(parser.getName())) {
                    count++;
                }
            }
        } catch (XmlPullParserException | IOException e) {
            return count;
        }
        return count;
    }
}
