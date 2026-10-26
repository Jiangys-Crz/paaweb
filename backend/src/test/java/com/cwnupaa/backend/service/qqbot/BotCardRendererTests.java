package com.cwnupaa.backend.service.qqbot;

import com.cwnupaa.backend.entity.FileSystem;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.FileSystemMapper;
import com.cwnupaa.backend.service.FileAccessPolicy;
import com.cwnupaa.backend.service.FileStorageService;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BotCardRendererTests {
    private final FileSystemMapper files = mock(FileSystemMapper.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final FileAccessPolicy policy = mock(FileAccessPolicy.class);
    private final BotCardRenderer renderer = new BotCardRenderer(files, storage, policy);

    @Test void rendersReadablePngCardsWithRatingAndWhiteTrackerBackgrounds() throws Exception {
        Users user = new Users(); user.setUserAccount("Alice");
        var profile = new HydroBotClient.HydroProfile(1675, 1800, 8, "Expert", "#3b82f6", 262,
                List.of(new HydroBotClient.Difficulty("800+", 100, 52.3), new HydroBotClient.Difficulty("1400+", 30, 17.9), new HydroBotClient.Difficulty("2000+", 10, 6.9), new HydroBotClient.Difficulty("待评级", 60, 22.9)));
        byte[] rating = renderer.rating(user, profile), tracker = renderer.tracker(user, Map.of(1L, 100L, 2L, 30L, 3L, 10L, 4L, 40L, 5L, 50L));
        var ratingImage = ImageIO.read(new ByteArrayInputStream(rating));
        var trackerImage = ImageIO.read(new ByteArrayInputStream(tracker));
        assertThat(ratingImage.getWidth()).isEqualTo(860);
        assertThat(ratingImage.getRGB(0, 0) & 0xffffff).isEqualTo(0x3b82f6);
        assertThat(trackerImage.getRGB(0, 0) & 0xffffff).isEqualTo(0xffffff);
        assertThat(rating.length).isGreaterThan(15000);
        assertThat(tracker.length).isGreaterThan(15000);
        Path output = Path.of("target", "qqbot-preview"); Files.createDirectories(output);
        Files.write(output.resolve("rating.png"), rating); Files.write(output.resolve("tracker.png"), tracker);
    }

    @Test void privateAvatarNeverReachesFileStorage() {
        Users user = new Users(); user.setUserAccount("Alice"); user.setAvatarFileId(9L);
        FileSystem file = new FileSystem(); file.setFileId(9L); file.setUrl("http://127.0.0.1/private");
        when(files.selectById(9L)).thenReturn(file); when(policy.canAccess(file, null)).thenReturn(false);
        renderer.tracker(user, Map.of());
        verifyNoInteractions(storage);
    }

    @Test void nonImageAvatarIsRejectedWithoutRemoteLoading() throws Exception {
        assertThat(BotCardRenderer.decodeImage("http://169.254.169.254/latest/meta-data".getBytes())).isNull();
    }
}
