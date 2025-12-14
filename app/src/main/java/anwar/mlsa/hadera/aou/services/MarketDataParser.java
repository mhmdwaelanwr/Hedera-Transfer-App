package anwar.mlsa.hadera.aou.services;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.HashMap;

import anwar.mlsa.hadera.aou.models.MarketData;

public class MarketDataParser {
    private static final String TAG = "MarketDataParser";

    public static MarketData parseCoinGeckoResponse(String response, String fiatCurrency) {
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(response, JsonObject.class);
            
            if (root.has("hedera-hashgraph")) {
                JsonObject hbarData = root.getAsJsonObject("hedera-hashgraph");
                
                double price = hbarData.has(fiatCurrency) ? hbarData.get(fiatCurrency).getAsDouble() : 0.0;
                double changePercent24h = hbarData.has(fiatCurrency + "_24h_change") ? 
                    hbarData.get(fiatCurrency + "_24h_change").getAsDouble() : 0.0;
                
                // Calculate absolute change from percentage
                double change24h = price * (changePercent24h / 100.0);
                
                return new MarketData(price, change24h, changePercent24h, fiatCurrency.toUpperCase());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing CoinGecko response", e);
        }
        
        return null;
    }

    /**
     * Parse historical price data from CoinGecko API.
     * 
     * NOTE: This method is currently incomplete and returns an empty map.
     * It is intended for future enhancement to support historical price tracking.
     * 
     * When fully implemented, this method should:
     * 1. Parse the "prices" array from CoinGecko's market_chart endpoint
     * 2. Extract [timestamp, price] pairs
     * 3. Return a map of timestamp -> price for historical lookups
     * 
     * Current usage: Not actively used in the application.
     * Future usage: Transaction history export with historical prices.
     * 
     * @param response JSON response from CoinGecko market_chart API
     * @param fiatCurrency The fiat currency code
     * @return Map of timestamp to price (currently empty)
     */
    public static HashMap<String, Double> parseHistoricalPrice(String response, String fiatCurrency) {
        HashMap<String, Double> prices = new HashMap<>();
        
        // TODO: Implement full historical price parsing when needed
        // This is intentionally left incomplete as historical pricing
        // is not required for current feature set
        
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(response, JsonObject.class);
            
            if (root.has("prices")) {
                // CoinGecko returns prices as [[timestamp, price], ...]
                // Example implementation:
                // JsonArray pricesArray = root.getAsJsonArray("prices");
                // for (JsonElement element : pricesArray) {
                //     JsonArray pair = element.getAsJsonArray();
                //     long timestamp = pair.get(0).getAsLong();
                //     double price = pair.get(1).getAsDouble();
                //     prices.put(String.valueOf(timestamp), price);
                // }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing historical price response", e);
        }
        
        return prices;
    }
}
