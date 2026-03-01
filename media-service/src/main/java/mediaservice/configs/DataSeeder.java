package mediaservice.configs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.models.*;


import mediaservice.models.enums.ReactionTargetType;
import mediaservice.models.enums.ReactionType;
import mediaservice.models.enums.VisibilityType;
import mediaservice.repositories.*;
import mediaservice.services.S3Service;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Tạo dữ liệu mẫu cho môi trường dev / seed.
 * - Avatar users được generate qua UI-Avatars rồi upload lên S3.
 * - Ảnh bài post được download từ picsum.photos rồi upload lên S3.
 * - Seed thêm reactions + comments để các counter không bằng 0.
 *
 * Guard: bỏ qua nếu DB đã có dữ liệu.
 * Chạy với: mvn spring-boot:run -Dspring-boot.run.profiles=seed
 */
@Slf4j
@Component
@Profile({"dev", "seed"})
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserAccountRepository userAccountRepository;
    private final PostRepository        postRepository;
    private final MediaRepository       mediaRepository;
    private final ReactionRepository    reactionRepository;
    private final CommentRepository     commentRepository;
    private final S3Service             s3Service;

    @Override
    @Transactional
    public void run(String... args) {
        if (postRepository.count() > 0) {
            log.info("[DataSeeder] DB already has data – skipping seed.");
            return;
        }

        log.info("[DataSeeder] Seeding database + uploading to S3 …");

        /* ── 1. Users  (avatars → S3) ──────────────────────── */
        UserAccount u1 = user("nguyen.nhan",   "Nguyễn Nhân",   "Nguyen+Nhan",   "6366f1");
        UserAccount u2 = user("tran.minhkhoa", "Trần Minh Khoa", "Tran+Minh+Khoa","10b981");
        UserAccount u3 = user("le.thuhuong",   "Lê Thu Hương",   "Le+Thu+Huong",  "f43f5e");
        UserAccount u4 = user("pham.vanlong",  "Phạm Văn Long",  "Pham+Van+Long", "f59e0b");
        userAccountRepository.saveAll(List.of(u1, u2, u3, u4));

        /* ── 2. Posts ───────────────────────────────────────── */
        Post p1 = post(u2, "Vừa đi leo núi Bà Đen về, cảnh đẹp quá trời 😍🏔️ "
                + "Ai muốn đi lần sau cùng không, tag mình nhé!");
        Post p2 = post(u1, "Hôm nay học được một kỹ thuật mới trong React 🚀 "
                + "Cảm giác productive lắm! #coding #reactjs");
        Post p3 = post(u3, "Cuối tuần tự làm bánh kem tặng mẹ 🎂✨ "
                + "Lần đầu làm mà ra lò đẹp vậy, không biết có ngon không nữa 😂");
        Post p4 = post(u2, "Tìm được quán cà phê view đẹp lắm mọi người ơi! "
                + "Ai ở Sài Gòn thì kéo nhau đi nhé ☕🌿");
        Post p5 = post(u1, "Chào mọi người! Hôm nay là một ngày tuyệt vời 🌟 "
                + "Hy vọng mọi người có ngày tốt lành nhé!");
        Post p6 = post(u3, "Chiều nay team mình ra công viên chụp ảnh, trời đẹp quá 🌤️📸");
        Post p7 = post(u4, "Chia sẻ kinh nghiệm học tiếng Anh từ con số 0 lên IELTS 7.0 "
                + "trong 1 năm 📚✍️ Ai cần tips thì comment bên dưới nhé!");
        postRepository.saveAll(List.of(p1, p2, p3, p4, p5, p6, p7));

        /* ── 3. Media  (ảnh picsum → download → S3) ────────── */
        img(p1, "hike-0",  "https://picsum.photos/seed/hike1/800/600",  0);
        img(p1, "hike-1",  "https://picsum.photos/seed/hike2/800/600",  1);
        img(p1, "hike-2",  "https://picsum.photos/seed/hike3/800/600",  2);

        img(p3, "cake-0",  "https://picsum.photos/seed/cake99/700/700", 0);

        img(p4, "cafe-0",  "https://picsum.photos/seed/cafe11/800/600", 0);
        img(p4, "cafe-1",  "https://picsum.photos/seed/cafe22/800/600", 1);

        img(p6, "park-0",  "https://picsum.photos/seed/park1/800/600",  0);
        img(p6, "park-1",  "https://picsum.photos/seed/park2/800/600",  1);
        img(p6, "park-2",  "https://picsum.photos/seed/park3/800/600",  2);
        img(p6, "park-3",  "https://picsum.photos/seed/park4/800/600",  3);

        img(p7, "study-0", "https://picsum.photos/seed/study1/800/500", 0);
        img(p7, "study-1", "https://picsum.photos/seed/study2/800/500", 1);
        img(p7, "study-2", "https://picsum.photos/seed/study3/800/500", 2);
        img(p7, "study-3", "https://picsum.photos/seed/study4/800/500", 3);
        img(p7, "study-4", "https://picsum.photos/seed/study5/800/500", 4);

        /* ── 4. Reactions (LIKE) ────────────────────────────── */
        // p1: u1, u3, u4 like
        react(u1, p1.getId()); react(u3, p1.getId()); react(u4, p1.getId());
        // p2: u2, u3 like
        react(u2, p2.getId()); react(u3, p2.getId());
        // p3: u1, u2, u4 like
        react(u1, p3.getId()); react(u2, p3.getId()); react(u4, p3.getId());
        // p4: u1, u3, u4 like
        react(u1, p4.getId()); react(u3, p4.getId()); react(u4, p4.getId());
        // p5: u2, u3, u4 like
        react(u2, p5.getId()); react(u3, p5.getId()); react(u4, p5.getId());
        // p6: u1, u2, u4 like
        react(u1, p6.getId()); react(u2, p6.getId()); react(u4, p6.getId());
        // p7: tất cả 4 người like
        react(u1, p7.getId()); react(u2, p7.getId());
        react(u3, p7.getId()); react(u4, p7.getId());

        /* ── 5. Comments ────────────────────────────────────── */
        comment(u1, p1, "Nhìn ảnh thôi là muốn xách ba lô đi rồi 😍");
        comment(u3, p1, "Tag mình với nhé, muốn đi lắm!");
        comment(u2, p2, "Kỹ thuật gì vậy bro? Share link đi 👀");
        comment(u4, p2, "React thì chỉ có bào thôi 🔥");
        comment(u1, p3, "Trông ngon quá, ship cho mình một miếng đi 😂");
        comment(u2, p3, "Tài thật, lần đầu mà làm được như này!");
        comment(u3, p4, "Quán ở đâu vậy? Địa chỉ đi bạn ơi ☕");
        comment(u4, p6, "Ảnh đẹp ghê, máy gì vậy bạn?");
        comment(u1, p7, "Cảm ơn bạn, mình đang cần tips này lắm 📚");
        comment(u2, p7, "7.0 trong 1 năm? Ghê thật!");
        comment(u3, p7, "Bạn học trung tâm hay tự học?");

        log.info("[DataSeeder] ✔ Seed complete  users={} posts={} reactions={} comments={}",
                userAccountRepository.count(), postRepository.count(),
                reactionRepository.count(), commentRepository.count());
    }

    /* ══════════════ helpers ══════════════════════════════════ */

    private UserAccount user(String username, String displayName,
                             String urlName, String bgHex) {
        // Generate avatar via UI-Avatars and upload to S3
        String avatarUrl = fetchAndUpload(
                "https://ui-avatars.com/api/?name=" + urlName
                        + "&background=" + bgHex + "&color=fff&size=200&format=png",
                "avatar-" + username,
                "social/avatars");
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setDisplayName(displayName);
        u.setAvatarUrl(avatarUrl);
        return u;
    }

    private Post post(UserAccount author, String caption) {
        Post p = new Post();
        p.setAccount(author);
        p.setCaption(caption);
        p.setVisibility(VisibilityType.PUBLIC);
        return p;
    }

    private void img(Post post, String key, String sourceUrl, int order) {
        String s3Url = fetchAndUpload(sourceUrl, "post-" + key, "social/posts");
        ImageMedia m = new ImageMedia();
        m.setUrl(s3Url);
        m.setOrderIndex(order);
        m.setContent(post);
        mediaRepository.save(m);
    }

    private void react(UserAccount account, String postId) {
        Reaction r = new Reaction();
        r.setAccount(account);
        r.setTargetId(postId);
        r.setTargetType(ReactionTargetType.POST);
        r.setReactionType(ReactionType.LIKE);
        reactionRepository.save(r);
    }

    private void comment(UserAccount account, Post post, String text) {
        Comment c = new Comment();
        c.setAccount(account);
        c.setContent(post);
        c.setText(text);
        c.setDepth(0);
        commentRepository.save(c);
    }

    /**
     * Download image từ sourceUrl (tự follow redirect) và upload lên S3.
     * Fallback về sourceUrl nếu có lỗi.
     */
    private String fetchAndUpload(String sourceUrl, String key, String folder) {
        try {
            HttpURLConnection conn = openConnection(sourceUrl);
            // Follow redirects manually (e.g. picsum → Unsplash CDN)
            int status = conn.getResponseCode();
            int maxRedirects = 5;
            while ((status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == 307 || status == 308)
                    && maxRedirects-- > 0) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                conn = openConnection(location);
                status = conn.getResponseCode();
            }

            if (status != HttpURLConnection.HTTP_OK) {
                log.warn("[DataSeeder] ✗ HTTP {} for {} – using source URL", status, key);
                conn.disconnect();
                return sourceUrl;
            }

            String contentType = conn.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                contentType = "image/jpeg";
            }
            String ext = contentType.contains("png") ? ".png" : ".jpg";

            try (InputStream in = conn.getInputStream()) {
                String s3Url = s3Service.uploadFile(in, key + ext, contentType, folder);
                log.info("[DataSeeder] ✔ Uploaded {} → {}", key, s3Url);
                return s3Url;
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            log.warn("[DataSeeder] ✗ Upload failed for {} – falling back. Cause: {}",
                    key, e.getMessage());
            return sourceUrl;
        }
    }

    private HttpURLConnection openConnection(String rawUrl) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(rawUrl).openConnection();
        conn.setInstanceFollowRedirects(false); // manual handling above
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (DataSeeder/1.0)");
        conn.connect();
        return conn;
    }
}
