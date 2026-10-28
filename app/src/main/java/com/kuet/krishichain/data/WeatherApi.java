package com.kuet.krishichain.data;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherApi {
    /** latitude and longitude are comma-separated lists, one entry per place (same order). */
    @GET("v1/forecast")
    Call<List<WeatherDto>> forecast(
            @Query(value = "latitude", encoded = true) String latitudes,
            @Query(value = "longitude", encoded = true) String longitudes,
            @Query("current") String current,
            @Query("timezone") String timezone);
}