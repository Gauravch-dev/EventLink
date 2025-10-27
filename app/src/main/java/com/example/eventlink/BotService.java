package com.example.eventlink;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface BotService {
    // POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key=API_KEY
    @POST("v1beta/models/{model}:generateContent")
    Call<GeminiResponse> generateContent(
            @Path("model") String model,
            @Query("key") String apiKey,
            @Body GeminiRequest body
    );
}
