package Utils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {

    public static boolean isCapacityReached(String filePath, int maxUsers) {
        File file = new File(filePath);
        if (!file.exists()) return false;

        int count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            while (br.readLine() != null) count++;
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            return true;
        }
        return count >= maxUsers;
    }

    public static boolean isItemLimitReached(String filePath, int maxItems) {
        File file = new File(filePath);
        if (!file.exists()) return false;

        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (reader.readLine() != null) count++;
        } catch (IOException e) {
            System.err.println("Error reading items file: " + e.getMessage());
            return true;
        }
        return count >= maxItems;
    }

    public static String readFileAsString(String filePath) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            return "Could not load items.";
        }
        return content.toString().trim();
    }

    public static boolean isDuplicateName(String filePath, String uniqueName) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length >= 1 && tokens[0].trim().equalsIgnoreCase(uniqueName)) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
        return false;
    }

    public static boolean appendLineToFile(String filePath, String line) {
        try (FileWriter fw = new FileWriter(filePath, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(line);
            return true;
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
            return false;
        }
    }

    public static boolean isDuplicateItem(String filePath, String itemName) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens[0].trim().equalsIgnoreCase(itemName)) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading items file: " + e.getMessage());
        }
        return false;
    }

    public static String removeAccountByName(String filePath, String uniqueName, int requestNumber) {
        File inputFile = new File(filePath);
        File tempFile = new File(filePath + ".tmp");
        boolean found = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length >= 1 && tokens[0].trim().equalsIgnoreCase(uniqueName)) {
                    found = true;
                    continue;
                }
                writer.println(line);
            }
        } catch (IOException e) {
            System.err.println("Error processing accounts file: " + e.getMessage());
            return "Deregister-denied RQ#" + requestNumber + " Reason: Internal server error";
        }

        if (!inputFile.delete() || !tempFile.renameTo(inputFile)) {
            return "Deregister-denied RQ#" + requestNumber + " Reason: File processing error";
        }

        return found ? "Deregistered RQ#" + requestNumber : "Deregister-denied RQ#" + requestNumber + " Reason: Account not found";
    }

    public static List<RegistrationInfo> getAllRegisteredBuyers(String filePath) {
        List<RegistrationInfo> buyers = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length >= 3) {
                    String uniqueName = tokens[0].trim();
                    String role = tokens[1].trim();
                    int udpPort = Integer.parseInt(tokens[2].split("#")[1].trim());

                    if (role.equalsIgnoreCase("buyer")) {
                        buyers.add(new RegistrationInfo(uniqueName, role, "127.0.0.1", udpPort, 0));
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading accounts file: " + e.getMessage());
        }

        return buyers;
    }
}
