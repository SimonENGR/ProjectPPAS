package Utils;

public class MessageParser {

    public static RegistrationInfo parseRegistrationMessage(String message) throws IllegalArgumentException {
        // Expected format: "register,uniqueName,role,ipAddress,udpPort,tcpPort"
        String[] tokens = message.split(",");

        if (tokens.length != 6) {
            throw new IllegalArgumentException("Invalid registration message format");
        }

        String uniqueName = tokens[1].trim();
        String role = tokens[2].trim().toLowerCase();
        String ipAddress = tokens[3].trim();

        int udpPort;
        int tcpPort;
        try {
            udpPort = Integer.parseInt(tokens[4].trim());
            tcpPort = Integer.parseInt(tokens[5].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("UDP and TCP ports must be integers", e);
        }

        return new RegistrationInfo(uniqueName, role, ipAddress, udpPort, tcpPort);
    }

    public static boolean isGetAllItemsRequest(String message) {
        return message.trim().equalsIgnoreCase("get_all_items");
    }
}
