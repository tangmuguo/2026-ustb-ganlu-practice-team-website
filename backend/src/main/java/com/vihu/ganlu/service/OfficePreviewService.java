package com.vihu.ganlu.service;

import java.io.IOException;
import java.nio.file.Path;

public interface OfficePreviewService {
    Path convertToPdf(Path sourceFile, Path targetFile) throws IOException;
}
