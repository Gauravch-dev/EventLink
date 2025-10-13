package com.example.eventlink;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import com.example.eventlink.GeminiIn;
import com.example.eventlink.GeminiOut;

public interface BotService {
    @POST("/gemini_chat")
    Call<GeminiOut> geminiChat(@Body GeminiIn body);
}
