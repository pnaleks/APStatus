package pnapp.tools.apstatus;

import java.net.InetSocketAddress;

public interface ITransmitter {
    void send(String message);
    void listen(InetSocketAddress address);

    public interface Callback {
        void onDuty();
        void onIdle();
        void onError(String message);
    }
}
