package anwar.mlsa.hadera.aou;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RequestNetworkController {
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final int REQUEST_PARAM = 0;
    public static final int REQUEST_BODY = 1;
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static RequestNetworkController mInstance;
    private final OkHttpClient client;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    private RequestNetworkController() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized RequestNetworkController getInstance() {
        if (mInstance == null) {
            mInstance = new RequestNetworkController();
        }
        return mInstance;
    }

    public void execute(final RequestNetwork requestNetwork, String method, String url, final String tag, final RequestNetwork.RequestListener requestListener) {
        Context context = requestNetwork.getContext();
        if (!ConnectivityUtil.isConnected(context)) {
            requestListener.onErrorResponse(tag, "No internet connection");
            return;
        }

        Request.Builder reqBuilder = new Request.Builder();
        Headers.Builder headerBuilder = new Headers.Builder();

        if (requestNetwork.getHeaders() != null) {
            for (HashMap.Entry<String, Object> header : requestNetwork.getHeaders().entrySet()) {
                headerBuilder.add(header.getKey(), String.valueOf(header.getValue()));
            }
        }
        reqBuilder.headers(headerBuilder.build());

        RequestBody reqBody = null;
        if (!method.equals(GET)) {
            if (requestNetwork.getRequestType() == REQUEST_PARAM) {
                FormBody.Builder formBuilder = new FormBody.Builder();
                if (requestNetwork.getParams() != null) {
                    for (HashMap.Entry<String, Object> param : requestNetwork.getParams().entrySet()) {
                        formBuilder.add(param.getKey(), String.valueOf(param.getValue()));
                    }
                }
                reqBody = formBuilder.build();
            } else { 
                reqBody = RequestBody.create(JSON, new Gson().toJson(requestNetwork.getParams()));
            }
        }

        if (method.equals(GET) && requestNetwork.getRequestType() == REQUEST_PARAM && requestNetwork.getParams().size() > 0) {
            HttpUrl.Builder httpBuilder = HttpUrl.parse(url).newBuilder();
            for (HashMap.Entry<String, Object> param : requestNetwork.getParams().entrySet()) {
                httpBuilder.addQueryParameter(param.getKey(), String.valueOf(param.getValue()));
            }
            reqBuilder.url(httpBuilder.build());
        } else {
            reqBuilder.url(url);
        }

        reqBuilder.method(method, reqBody);
        reqBuilder.tag(context);

        Request req = reqBuilder.build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                mainThreadHandler.post(() -> requestListener.onErrorResponse(tag, e.getMessage()));
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                final String responseBody = response.body().string().trim();
                final HashMap<String, Object> responseHeaders = new HashMap<>();
                Headers headers = response.headers();
                for (String name : headers.names()) {
                    responseHeaders.put(name, headers.get(name));
                }
                mainThreadHandler.post(() -> {
                    try {
                        requestListener.onResponse(tag, responseBody, responseHeaders);
                    } finally {
                        response.close();
                    }
                });
            }
        });
    }

    public void cancelAllRequests(Context context) {
        for (Call call : client.dispatcher().queuedCalls()) {
            if (call.request().tag().equals(context)) {
                call.cancel();
            }
        }
        for (Call call : client.dispatcher().runningCalls()) {
            if (call.request().tag().equals(context)) {
                call.cancel();
            }
        }
    }
}
