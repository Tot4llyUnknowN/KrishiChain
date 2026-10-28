package com.kuet.krishichain.data;

/** One element of Open-Meteo's answer (with several coordinates the answer is a JSON array). */
public class WeatherDto {
    public Current current;

    public static class Current {
        public String time;
        public Double precipitation; // mm in the last interval; may be null
    }
}