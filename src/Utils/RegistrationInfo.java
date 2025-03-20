package Utils;

public class RegistrationInfo {
    private String uniqueName;
    private String role;      // "buyer" or "seller"
    private String ipAddress;
    private int udpPort;
    private int tcpPort;

    public RegistrationInfo(String uniqueName, String role, String ipAddress, int udpPort, int tcpPort) {
        this.uniqueName = uniqueName;
        this.role = role;
        this.ipAddress = ipAddress;
        this.udpPort = udpPort;
        this.tcpPort = tcpPort;
    }

    // Getters and optionally setters
    public String getUniqueName() { return uniqueName; }
    public String getRole() { return role; }
    public String getIpAddress() { return ipAddress; }
    public int getUdpPort() { return udpPort; }
    public int getTcpPort() { return tcpPort; }

    @Override
    public String toString() {
        return "RegistrationInfo{" +
                "uniqueName='" + uniqueName + '\'' +
                ", role='" + role + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", udpPort=" + udpPort +
                ", tcpPort=" + tcpPort +
                '}';
    }
}