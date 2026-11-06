/*
 * Render the user-supplied ICPC Tools resolver locally without a graphical display.
 * No contest data is embedded in this file.
 *
 * javac -cp '/path/to/resolver/lib/*' scripts/ResolverReferenceRender.java
 * java -Djava.awt.headless=true -cp 'scripts:/path/to/resolver/lib/*' \
 *   ResolverReferenceRender /path/to/contest-package /tmp/reference.png [team-id]
 * With team-id, render that team's award; otherwise render the standings.
 */
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.icpc.tools.contest.model.IAward;
import org.icpc.tools.contest.model.feed.DiskContestSource;
import org.icpc.tools.contest.model.internal.Contest;
import org.icpc.tools.contest.model.resolver.ResolutionUtil;
import org.icpc.tools.presentation.core.Presentation;
import org.icpc.tools.presentation.contest.internal.presentations.resolver.TeamAwardPresentation;
import org.icpc.tools.presentation.contest.internal.scoreboard.ScoreboardPresentation;

public class ResolverReferenceRender {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("Usage: ResolverReferenceRender contest-package output.png [team-id]");
        }
        DiskContestSource source = new DiskContestSource(new File(args[0]));
        Contest contest = source.loadContest(null);
        if (!source.waitForContestLoad()) throw new IllegalStateException("Contest did not load");
        Presentation presentation;
        if (args.length == 3) {
            String teamId = args[2];
            if (contest.getTeamById(teamId) == null) throw new IllegalArgumentException("Unknown team id");
            List<IAward> awards = new ArrayList<>();
            for (IAward award : contest.getAwards()) {
                if (award.getTeamIds() != null && Arrays.asList(award.getTeamIds()).contains(teamId)) awards.add(award);
            }
            TeamAwardPresentation award = new TeamAwardPresentation();
            award.setContest(contest);
            award.setSize(new Dimension(1920, 1080));
            award.init();
            award.cacheAwards(List.of(new ResolutionUtil.AwardStep(teamId, awards)));
            // The reference implementation fills the award cache in a background job.
            java.lang.reflect.Field cache = TeamAwardPresentation.class.getDeclaredField("cache");
            cache.setAccessible(true);
            for (int i = 0; i < 200 && cache.get(award) == null; i++) Thread.sleep(50);
            if (cache.get(award) == null) throw new IllegalStateException("Award cache did not load");
            award.setTeam(teamId);
            presentation = award;
        } else {
            // ResolverUI uses an anonymous ScoreboardPresentation with no title.
            ScoreboardPresentation board = new ScoreboardPresentation() {
                @Override public String getTitle() { return null; }
            };
            board.setProperty("clockOff");
            board.setContest(contest);
            board.setSize(new Dimension(1920, 1080));
            board.init();
            board.aboutToShow();
            board.setShowLegend(false);
            presentation = board;
        }
        presentation.setTimeMs(7000);
        presentation.setRepeatTimeMs(7000);
        BufferedImage image = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        presentation.paint(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", new File(args[1]));
        source.close();
        System.exit(0); // ContestSource may retain background worker threads.
    }
}
