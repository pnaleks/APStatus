package pnapp.tools.apstatus;

import android.annotation.SuppressLint;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.Key;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Абстрактный класс для реализации функций listen, read, write на tcp сокетах
 *
 * @author P.N. Alekseev
 * @author pnaleks@gmail.com
 */
@SuppressWarnings("unused")
public abstract class SocketTask implements Runnable {
    /** Регулярное выражение для поиска адресов в файле /proc/net/arp */
    private static final Pattern PATTERN_ARP = Pattern.compile("\\s*(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\s.*\\s(\\w\\w):(\\w\\w):(\\w\\w):(\\w\\w):(\\w\\w):(\\w\\w)\\s.*");
    /** Регулярное выражение для преобразования IP-адреса из строкового представления */
    private static final Pattern PATTERN_IP = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");
    /** Регулярное выражение для преобразования MAC-адреса из строкового представления */
    private static final Pattern PATTERN_MAC = Pattern.compile("(\\w\\w):(\\w\\w):(\\w\\w):(\\w\\w):(\\w\\w):(\\w\\w)");
    /** IP адрес локального интерфейса или удаленного хоста (зависит от реализации наследника) */
    protected String host;
    /** Номер локального или удаленного порта (зависит от реализации наследника) */
    protected int port;
    /** Идентификатор объекта. Обычно, это mac адрес интерфейса */
    protected long mac;
    /** Содержит сообщение об ошибке либо другую информацию */
    protected String message;
    /** Ключ для шифрования или дешифрования данных */
    protected Key key;
    /** Результат {@link InetAddress#getHostAddress()} вызванной внутри {@link Runnable#run()} */
    protected String hostAddress;
    /** Позволяет установить данные для идентификации и т.п. */
    protected Object tag;

    public SocketTask setTag(Object tag) { this.tag = tag; return this; }
    public Object getTag() { return tag; }

    @Override
    abstract public void run();

    public long getMac() {
        if ( mac == 0 ) {
            if ( host != null ) mac = ARP(host);
        }
        return mac;
    }

    public String getHost() {
        if ( host == null ) {
            if ( mac != 0 ) host = InARP(mac);
        }
        return host;
    }

    public String getHostAddress() { return hostAddress; }

    public int getPort() { return port; }

    public void go() { new Thread(this).start(); }

    public String getMessage() { return message; }

    /** Простое сравнение по имени хоста и номеру порта */
    public boolean match(String host, int port) {
        return this.port == port && ( (this.host == null && host == null) || (host != null && host.equals(this.host)) );
    }
    /**
     * Для сравнения используются результаты вызова функций {@link #getPort()} и {@link #getHostAddress()}, которые
     * могут быть переопределены в подклассе. Если otherPort равен нулю, сравнение происходит только по адресу.
     */
    public boolean matchByAddress(String otherAddress, int otherPort) {
        if ( otherPort != 0 && getPort() != otherPort ) return false;
        String address = getHostAddress();
        return (address == null && otherAddress == null) || (address != null && address.equals(otherAddress));
    }

    public SocketTask setKey(Key key) {
        this.key = key;
        return this;
    }
    public Key getKey() { return key; }

    @Override
    public String toString() { return  getClass().getSimpleName() + " on " + host + ':' + port; }

    /**
     * Определение MAC по IP адресу используя данные кэша ARP в файле /proc/net/arp
     *
     * @param hostAddress IP-адрес
     * @return MAC-адрес или ноль, если адрес не найден в кэше или произошла ошибка
     */
    public static long ARP(String hostAddress) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File("/proc/net/arp")));
            String s;
            while ( (s = reader.readLine()) != null ) {
                Matcher m = PATTERN_ARP.matcher(s);
                if ( m.matches() && hostAddress.equals(m.group(1))) {
                    String MAC = m.group(2) + m.group(3) + m.group(4) + m.group(5) + m.group(6) + m.group(7);
                    return Long.valueOf(MAC,16);
                }
            }
        } catch (IOException ignore) {}
        return 0;
    }

    /**
     * Определение IP по MAC адресу используя данные кэша ARP в файле /proc/net/arp
     *
     * @param mac MAC-адрес
     * @return IP-адрес или null, если mac не найден в кэше или произошла ошибка
     */
    public static String InARP(long mac) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File("/proc/net/arp")));
            String s;
            while ( (s = reader.readLine()) != null ) {
                Matcher m = PATTERN_ARP.matcher(s);
                if ( m.matches() ) {
                    String MAC = m.group(2) + m.group(3) + m.group(4) + m.group(5) + m.group(6) + m.group(7);
                    if ( mac == Long.valueOf(MAC,16) ) return m.group(1);
                }
            }
        } catch (IOException ignore) {}
        return null;
    }

    @SuppressLint("DefaultLocale")
    public static String ipToString(int ip) {
        return String.format("%d.%d.%d.%d", ip & 0x0ff, (ip >>> 8) & 0x0ff, (ip >>> 16) & 0x0ff, ip >>> 24);
    }

    @SuppressLint("DefaultLocale")
    public static String ipToString(byte[] ip) {
        return String.format("%d.%d.%d.%d", ip[0], ip[1], ip[2], ip[3]);
    }

    /**
     * Преобразует строковое представление IP-адреса в int. Только для IPv4
     *
     * @param hostAddress IP-адрес в виде строки типа 127.0.0.1
     * @return IP-адрес или -1 если hostAddress имеет неправильный формат
     */
    public static int ip(String hostAddress) {
        Matcher m = PATTERN_IP.matcher(hostAddress);
        if ( m.matches() ) {
            return
                    (Integer.parseInt( m.group(4) ) << 24) |
                            (Integer.parseInt( m.group(3) ) << 16) |
                            (Integer.parseInt( m.group(2) ) <<  8) |
                            (Integer.parseInt( m.group(1) )      );
        }
        return -1;
    }

    public static int ip(byte[] bytes) {
        if ( bytes.length != 4 ) return -1;
        return ((bytes[3] & 0x0ff) << 24 ) |
               ((bytes[2] & 0x0ff) << 16 ) |
               ((bytes[1] & 0x0ff) << 8) |
                (bytes[0] & 0x0ff);
    }

    public static String macToString(long mac) {
        return String.format("%02x:%02x:%02x:%02x:%02x:%02x",
                mac & 0x0ff, (mac >>> 8) & 0x0ff, (mac >>> 16) & 0x0ff, (mac >>> 24) & 0x0ff, (mac >>> 32) & 0x0ff, (mac >>> 40) & 0x0ff);
    }

    public static String macToString(byte[] mac) {
        return String.format("%02x:%02x:%02x:%02x:%02x:%02x", mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
    }

    public static long mac(String hardwareAddress) {
        if ( hardwareAddress != null ) {
            Matcher m = PATTERN_MAC.matcher(hardwareAddress);
            if (m.matches()) {
                String MAC = m.group(1) + m.group(2) + m.group(3) + m.group(4) + m.group(5) + m.group(6);
                return Long.valueOf(MAC, 16);
            }
        }
        return -1L;
    }

    public static long mac(byte[] bytes) {
        if ( bytes.length != 6 ) return -1L;
        return  (((long) bytes[0] & 0x0ffL) << 40) |
                (((long) bytes[1] & 0x0ffL) << 32) |
                (((long) bytes[2] & 0x0ffL) << 24) |
                (((long) bytes[3] & 0x0ffL) << 16) |
                (((long) bytes[4] & 0x0ffL) << 8) |
                ((long) bytes[5] & 0x0ffL);
    }

    public static int getLocalIpAddress(long mac) {
        try {
            ArrayList<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for(NetworkInterface i : interfaces) {
                byte[] bytes = i.getHardwareAddress();
                if ( bytes == null ) continue;
                if ( mac(bytes) == mac ) {
                    ArrayList<InetAddress> addresses = Collections.list(i.getInetAddresses());
                    for ( InetAddress a : addresses ) {
                        if ( !a.isLoopbackAddress() && a.getAddress().length == 4 ) return ip(a.getAddress());
                    }
                }
            }
        } catch (SocketException ignore) {}
        return -1;
    }

    public static boolean isLoopback(String address) {
        return "localhost".equalsIgnoreCase(address) || (ip(address)&255)  == 127;
    }
}
