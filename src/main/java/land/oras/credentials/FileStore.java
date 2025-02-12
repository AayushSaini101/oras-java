package land.oras.credentials;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import land.oras.exception.ConfigLoadingException;

/**
 * FileStore implements a credentials store using a configuration file
 * to keep the credentials in plain-text.
 *
 * Reference: https://docs.docker.com/engine/reference/commandline/cli/#docker-cli-configuration-file-configjson-properties
 */
public class FileStore {

    private final boolean disablePut;
    private final Config config;

    public static final String ERR_PLAINTEXT_PUT_DISABLED = "Putting plaintext credentials is disabled";
    public static final String ERR_BAD_CREDENTIAL_FORMAT = "Bad credential format";

    /**
     * Constructor for FileStore.
     *
     * @param disablePut boolean flag to disable putting credentials in plaintext.
     * @param config configuration instance.
     */
    public FileStore(boolean disablePut, Config config) {
        this.disablePut = disablePut;
        this.config = Objects.requireNonNull(config, "Config cannot be null");
    }

    /**
     * Creates a new FileStore based on the given configuration file path.
     *
     * @param configPath Path to the configuration file.
     * @return FileStore instance.
     * @throws Exception if loading the configuration fails.
     */
    public static FileStore newFileStore(String configPath) throws Exception {
        Config cfg = Config.load(configPath);
        return new FileStore(false, cfg);
    }

    /**
     * Retrieves credentials for the given server address.
     *
     * @param serverAddress Server address.
     * @return Credential object.
     * @throws Exception if retrieval fails.
     */
    public Credential get(String serverAddress) throws Exception {
        return config.getCredential(serverAddress);
    }

    /**
     * Saves credentials for the given server address.
     *
     * @param serverAddress Server address.
     * @param credential Credential object.
     * @throws Exception if saving fails.
     */
    public void put(String serverAddress, Credential credential) throws Exception {
        if (disablePut) {
            throw new UnsupportedOperationException(ERR_PLAINTEXT_PUT_DISABLED);
        }
        validateCredentialFormat(credential);
        config.putCredential(serverAddress, credential);
    }

    /**
     * Deletes credentials for the given server address.
     *
     * @param serverAddress Server address.
     * @throws Exception if deletion fails.
     */
    public void delete(String serverAddress) throws Exception {
        config.deleteCredential(serverAddress);
    }

    /**
     * Validates the format of the credential.
     *
     * @param credential Credential object.
     * @throws Exception if the credential format is invalid.
     */
    private void validateCredentialFormat(Credential credential) throws Exception {
        if (credential.getUsername().contains(":")) {
            throw new IllegalArgumentException(ERR_BAD_CREDENTIAL_FORMAT + ": colons(:) are not allowed in username");
        }
    }

    /**
     * Nested Config class for configuration management.
     */
    public static class Config {
        private final ConcurrentHashMap<String, Credential> credentialStore = new ConcurrentHashMap<>();

    /**
     * Load configuration from a JSON file and populate the credential store.
     *
     * @param configPath Path to the JSON configuration file.
     * @return A Config instance with loaded credentials.
     * @throws ConfigLoadingException If the file cannot be read or parsed.
     */
    public static Config load(String configPath) throws ConfigLoadingException {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            // Read the file content
            String fileContent = new String(Files.readAllBytes(Paths.get(configPath)));
            // Parse JSON into a Map<String, Credential>
            Map<String, Credential> credentials = objectMapper.readValue(fileContent,
                new TypeReference<Map<String, Credential>>() {});

            // Create a new Config instance
            Config config = new Config();

            // Populate the credential store
            for (Map.Entry<String, Credential> entry : credentials.entrySet()) {
                String serverAddress = entry.getKey();
                Credential credential = entry.getValue();
                // Put the serverAddress and Credential into the credentialStore
                config.credentialStore.put(serverAddress, credential);
            }
            return config;
        } catch (IOException e) {
            // Handle issues related to file reading or file not found
            throw new ConfigLoadingException("Failed to read the configuration file: " + configPath, e);
        }
    }

        public Credential getCredential(String serverAddress) {
            return credentialStore.get(serverAddress);
        }

        public void putCredential(String serverAddress, Credential credential) {
            credentialStore.put(serverAddress, credential);
        }

        public void deleteCredential(String serverAddress) {
            credentialStore.remove(serverAddress);
        }
    }

    /**
     * Nested Credential class to represent username and password pairs.
     */
    public static class Credential {
        private  String username;
        private  String password;
         // Default constructor for the jackson for deserialization
        public Credential() {
        }

        public Credential(String username, String password) {
            this.username = Objects.requireNonNull(username, "Username cannot be null");
            this.password = Objects.requireNonNull(password, "Password cannot be null");
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }
}
