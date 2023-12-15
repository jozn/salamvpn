package app.firstvpn;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ListResponse {
    @SerializedName("Servers")
    private List<String> servers;

    @SerializedName("Config")
    private AndroidConfig config;

    public ListResponse(List<String> servers, AndroidConfig config) {
        this.servers = servers;
        this.config = config;
    }

    // Getters and setters
    public List<String> getServers() {
        return servers;
    }

    public void setServers(List<String> servers) {
        this.servers = servers;
    }

    public AndroidConfig getConfig() {
        return config;
    }

    public void setConfig(AndroidConfig config) {
        this.config = config;
    }
}
