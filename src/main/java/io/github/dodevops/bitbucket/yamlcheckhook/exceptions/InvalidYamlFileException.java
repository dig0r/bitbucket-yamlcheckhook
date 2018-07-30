package io.github.dodevops.bitbucket.yamlcheckhook.exceptions;

/**
 * The added or modified YAML file is not valid.
 */

public class InvalidYamlFileException extends Exception {
    private static final long serialVersionUID = 4476325871497661043L;
    private final String filePath;

    private final String scannerMessage;

    public InvalidYamlFileException(final String s, final String scannerMessage) {
        this.filePath = s;
        this.scannerMessage = scannerMessage;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getScannerMessage() {
        return scannerMessage;
    }
}
