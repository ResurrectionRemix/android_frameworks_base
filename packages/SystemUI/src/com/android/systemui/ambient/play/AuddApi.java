/*
 * Copyright (C) 2018 PixelExperience
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */

package com.android.systemui.ambient.play;

import android.util.Base64;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

class AuddApi {
    static String sendRequest(AmbientIndicationManager manager, byte[] micData) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL("https://api.audd.io/").openConnection();
            conn.setReadTimeout(30000);
            conn.setConnectTimeout(30000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(os, "UTF-8"));
            HashMap<String, String> params = new HashMap<>();
            params.put("api_token", "8ec90ef80fd1b750c990642d6e17ccb9");
            params.put("local_lan", "pt-BR");
            params.put("device_id", "0.9513109616541061545060969071");
            params.put("version", "2.0.0");
            params.put("app_id", "ngpampappnmepgilojfohadhhmbhlaek");
            params.put("audio_format", "wav");
            params.put("audio", Base64.encodeToString(micData, Base64.DEFAULT));
            writer.write(formatPostParams(params));
            writer.flush();
            writer.close();
            os.close();
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String line;
                StringBuilder response = new StringBuilder();
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        } catch (Exception e) {
            if (manager.DEBUG) e.printStackTrace();
        }
        return null;
    }

    private static String formatPostParams(HashMap<String, String> params) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (first) {
                first = false;
            } else {
                result.append("&");
            }
            result.append(Uri.encode(entry.getKey()));
            result.append("=");
            result.append(Uri.encode(entry.getValue()));
        }
        return result.toString();
    }
}