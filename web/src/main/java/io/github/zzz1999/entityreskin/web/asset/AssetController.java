package io.github.zzz1999.entityreskin.web.asset;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Authenticated asset upload. The owner is taken from the JWT principal, not the request body. */
@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(@RequestParam("kind") String kind,
                                 @RequestParam("file") MultipartFile file,
                                 Authentication authentication) {
        Asset asset = assetService.upload(authentication.getName(), kind, file);
        return new UploadResponse(asset.getSha256(), asset.getSize(), asset.getKind(),
                asset.getCreatedAt().toString());
    }

    public record UploadResponse(String sha256, long size, String kind, String createdAt) {
    }
}
