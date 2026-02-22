import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.proyect.auth.service.GameService;
import com.example.proyect.auth.service.UserService;
import com.example.proyect.persistence.classes.Game;
import com.example.proyect.persistence.classes.User;

@RestController
@RequestMapping("/api/games")
public class GameApiController {

    private final GameService gameService;
    private final UserService userService;

    public GameApiController(GameService gameService, UserService userService) {
        this.gameService = gameService;
        this.userService = userService;
    }

    @GetMapping("/saved")
    public List<SavedGameResponse> getSavedGames(Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userService.getByUsername(username);

        return gameService.getPausedGamesOfUser(currentUser.getUserId())
                .stream()
                .map(game -> toSavedGameResponse(currentUser.getUserId(), game))
                .toList();
    }

    private SavedGameResponse toSavedGameResponse(Long currentUserId, Game game) {
        if (!gameService.canUserAccessGame(currentUserId, game)) {
            throw new AccessDeniedException("No autorizado para ver esta partida");
        }

        Long rivalId = currentUserId.equals(game.getPlayer1Id()) ? game.getPlayer2Id() : game.getPlayer1Id();
        User rival = userService.getById(rivalId);

        return new SavedGameResponse(
                game.getId(),
                new RivalSummary(rival.getUserId(), rival.getUsername()),
                game.getStartedAt(),
                game.getEndedAt(),
                game.getState() != null ? game.getState().getTurn() : null,
                buildSnapshotSummary(game)
        );
    }

    private SnapshotSummary buildSnapshotSummary(Game game) {
        Map<String, Object> meta = game.getState() != null ? game.getState().getMeta() : null;
        Map<String, Object> snapshot = null;

        if (meta != null) {
            Object snapshotRaw = meta.get("snapshot");
            if (snapshotRaw instanceof Map<?, ?> snapshotMap) {
                snapshot = (Map<String, Object>) snapshotMap;
            }
        }

        List<String> snapshotKeys = snapshot == null ? List.of() : snapshot.keySet().stream().sorted().toList();

        return new SnapshotSummary(
                snapshot != null,
                snapshotKeys,
                snapshot == null ? 0 : snapshot.size()
        );
    }

    public record SavedGameResponse(
            Long gameId,
            RivalSummary rival,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            Integer currentTurn,
            SnapshotSummary snapshotSummary
    ) {}

    public record RivalSummary(Long userId, String username) {}

    public record SnapshotSummary(
            boolean hasSnapshot,
            List<String> keys,
            int topLevelEntries
    ) {}
}