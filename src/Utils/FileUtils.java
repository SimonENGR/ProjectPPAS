package Utils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {

    public static int readLastRequestNumber(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) return 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            return line != null ? Integer.parseInt(line.trim()) : 0;
        } catch (IOException | NumberFormatException e) {
            System.err.println("Error reading last RQ#: " + e.getMessage());
            return 0;
        }
    }

    public static void writeLastRequestNumber(String filePath, int lastRQ) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println(lastRQ);
        } catch (IOException e) {
            System.err.println("Error writing last RQ#: " + e.getMessage());
        }
    }

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

    public static boolean isAlreadySubscribed(String filePath, String itemName, RegistrationInfo buyer) {
        File file = new File(filePath);
        if (!file.exists()) return false;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length >= 4 &&
                        tokens[0].trim().equalsIgnoreCase(itemName) &&
                        tokens[2].trim().equals(buyer.getIpAddress()) &&
                        tokens[3].trim().equals(String.valueOf(buyer.getUdpPort()))) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("Error checking subscription: " + e.getMessage());
        }

        return false;
    }

    public static boolean addSubscription(String filePath, String itemName, RegistrationInfo buyer) {
        String line = itemName + "," + buyer.getUniqueName() + "," + buyer.getIpAddress() + "," + buyer.getUdpPort();
        return appendLineToFile(filePath, line);
    }

    public static boolean removeSubscription(String filePath, String itemName, RegistrationInfo buyer) {
        File inputFile = new File(filePath);
        File tempFile = new File(filePath + ".tmp");
        boolean found = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length >= 4 &&
                        tokens[0].trim().equalsIgnoreCase(itemName) &&
                        tokens[2].trim().equals(buyer.getIpAddress()) &&
                        tokens[3].trim().equals(String.valueOf(buyer.getUdpPort()))) {
                    found = true; // Skip this line (remove it)
                    continue;
                }
                writer.println(line); // Keep this line
            }

        } catch (IOException e) {
            System.err.println("Error processing subscriptions file: " + e.getMessage());
            return false;
        }

        // Replace original file with updated temp file
        if (!inputFile.delete() || !tempFile.renameTo(inputFile)) {
            System.err.println("Failed to replace subscriptions file.");
            return false;
        }

        return found;
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

    public static List<RegistrationInfo> getSubscribersForItem(String filePath, String itemName) {
        List<RegistrationInfo> subscribers = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length >= 4 && tokens[0].trim().equalsIgnoreCase(itemName)) {
                    String buyerName = tokens[1].trim();
                    String ip = tokens[2].trim();
                    int udpPort = Integer.parseInt(tokens[3].trim());
                    subscribers.add(new RegistrationInfo(buyerName, "buyer", ip, udpPort, 0));
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading subscriptions file: " + e.getMessage());
        }
        return subscribers;
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

    public static boolean removeItemFromFile(String filePath, String itemName) {
        File inputFile = new File(filePath);
        File tempFile = new File(filePath + ".tmp");
        boolean found = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length >= 1 && tokens[0].trim().equalsIgnoreCase(itemName)) {
                    found = true; // Skip this line (remove the item)
                    continue;
                }
                writer.println(line); // Keep this line
            }
        } catch (IOException e) {
            System.err.println("Error processing items file: " + e.getMessage());
            return false;
        }

        // Replace original file with updated temp file
        if (!inputFile.delete() || !tempFile.renameTo(inputFile)) {
            System.err.println("Failed to replace items file.");
            return false;
        }

        if (found) {
            System.out.println("Item '" + itemName + "' has been successfully removed from the auction.");
        } else {
            System.out.println("Item '" + itemName + "' was not found in the auction list.");
        }

        return found;
    }

    public static String getAuctionLine(String filePath, String itemName) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Assumes the first token is the item name
                String[] tokens = line.split(",");
                if (tokens.length > 0 && tokens[0].trim().equalsIgnoreCase(itemName)) {
                    return line;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading auctions file: " + e.getMessage());
        }
        return null;
    }

    public static boolean updateAuctionLine(String filePath, String itemName, String updatedLine) {
        File inputFile = new File(filePath);
        File tempFile = new File(filePath + ".tmp");
        boolean updated = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length > 0 && tokens[0].trim().equalsIgnoreCase(itemName)) {
                    writer.println(updatedLine);
                    updated = true;
                } else {
                    writer.println(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error updating auctions file: " + e.getMessage());
            return false;
        }

        // Replace original file with updated file
        if (!inputFile.delete() || !tempFile.renameTo(inputFile)) {
            System.err.println("Error replacing the auctions file during update.");
            return false;
        }

        if (updated) {
            System.out.println("Auction for item '" + itemName + "' updated successfully.");
        } else {
            System.out.println("Auction for item '" + itemName + "' not found. Update failed.");
        }
        return updated;
    }





}


