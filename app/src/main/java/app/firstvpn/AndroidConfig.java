package app.firstvpn;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AndroidConfig {
    @SerializedName("ColCp")
    private boolean colCp;

    @SerializedName("ColNf")
    private boolean colNf;

    public AndroidConfig(boolean colCp, boolean colNf) {
        this.colCp = colCp;
        this.colNf = colNf;
    }

    // Getters and setters
    public boolean isColCp() {
        return colCp;
    }

    public void setColCp(boolean colCp) {
        this.colCp = colCp;
    }

    public boolean isColNf() {
        return colNf;
    }

    public void setColNf(boolean colNf) {
        this.colNf = colNf;
    }
}
