package anwar.mlsa.hadera.aou;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
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
        Activity activity = requestNetwork.getActivity();
        if (!ConnectivityUtil.isConnected(activity)) {
            showNoInternetDialog(activity, tag, requestListener);
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

        Request req = reqBuilder.build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                activity.runOnUiThread(() -> requestListener.onErrorResponse(tag, e.getMessage()));
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                final String responseBody = response.body().string().trim();
                final HashMap<String, Object> responseHeaders = new HashMap<>();
                Headers headers = response.headers();
                for (String name : headers.names()) {
                    responseHeaders.put(name, headers.get(name));
                }
                activity.runOnUiThread(() -> {
                    try {
                        requestListener.onResponse(tag, responseBody, responseHeaders);
                    } finally {
                        response.close();
                    }
                });
            }
        });
    }
    
    private void showNoInternetDialog(Activity activity, String tag, RequestNetwork.RequestListener listener) {
        if (activity == null || activity.isFinishing()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_no_internet, null);
        builder.setView(dialogView).setCancelable(false);
        final AlertDialog dialog = builder.create();
        dialogView.findViewById(R.id.ok_button).setOnClickListener(v -> {
            dialog.dismiss();
            listener.onErrorResponse(tag, "No internet connection");
        });
        dialog.show();
    }
}
