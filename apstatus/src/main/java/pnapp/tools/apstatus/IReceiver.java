package pnapp.tools.apstatus;

import java.net.InetSocketAddress;

public interface IReceiver {
    void connect(InetSocketAddress address);

    public interface Callback {
        void onReceive(String message);
        void onError(String message);
    }
}
