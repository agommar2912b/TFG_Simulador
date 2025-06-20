package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.Game;
import org.example.Player;
import org.example.Position;
import org.example.Team;
import org.example.dto.team.TeamCreate;
import org.example.dto.team.TeamPatch;
import org.example.dto.team.TeamResponse;
import org.example.entity.PlayerEntity;
import org.example.entity.TeamEntity;
import org.example.exceptions.TeamNameExistException;
import org.example.exceptions.TeamNameNotExistException;
import org.example.exceptions.UserNotFoundNameException;
import org.example.service.TeamService;
import org.example.service.UserService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/users/{userId}/teams")
public class TeamController {
    private final TeamService teamService;


    @PostMapping("/simulate")
    public ResponseEntity<String> simulateGame(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> body) {

        String teamAName = (String) body.get("teamAName");
        String teamBName = (String) body.get("teamBName");

        List<String> localFaltas = ((List<?>) body.get("local_faltas")).stream().map(Object::toString).toList();
        List<String> localCorners = ((List<?>) body.get("local_corners")).stream().map(Object::toString).toList();
        List<String> localPenalty = ((List<?>) body.get("local_penalty")).stream().map(Object::toString).toList();
        List<String> visitanteFaltas = ((List<?>) body.get("visitante_faltas")).stream().map(Object::toString).toList();
        List<String> visitanteCorners = ((List<?>) body.get("visitante_corners")).stream().map(Object::toString).toList();
        List<String> visitantePenalty = ((List<?>) body.get("visitante_penalty")).stream().map(Object::toString).toList();

        TeamEntity teamAEntity = teamService.getByName(userId, teamAName).stream().findFirst()
            .orElseThrow(() -> new TeamNameNotExistException(teamAName));
        TeamEntity teamBEntity = teamService.getByName(userId, teamBName).stream().findFirst()
            .orElseThrow(() -> new TeamNameNotExistException(teamBName));

        Team teamA = new Team();
        teamA.setName(teamAName);
        Team teamB = new Team();
        teamB.setName(teamBName);

        // Asignar titulares, portero y suplentes para teamA
        List<PlayerEntity> allA = teamAEntity.getPlayers();
        PlayerEntity goalieAEntity = allA.stream()
                .filter(p -> p.getPosition() == Position.GOALKEEPER && p.isHasPlayed())
                .findFirst()
                .orElse(null);
        List<PlayerEntity> fieldPlayersAEntities = allA.stream()
                .filter(p -> p.getPosition() != Position.GOALKEEPER && p.isHasPlayed())
                .toList();
        List<PlayerEntity> substitutesAEntities = allA.stream()
                .filter(p -> !p.isHasPlayed())
                .toList();

        // Conversión a Player
        Player goalieA = null;
        if (goalieAEntity != null) {
            goalieA = new Player(
                goalieAEntity.getName(),
                goalieAEntity.getSkill(),
                goalieAEntity.getPosition(),
                goalieAEntity.isHasPlayed()
            );
        }
        List<Player> fieldPlayersA = fieldPlayersAEntities.stream()
                .map(pe -> new Player(pe.getName(), pe.getSkill(), pe.getPosition(), pe.isHasPlayed()))
                .toList();
        List<Player> substitutesA = substitutesAEntities.stream()
                .map(pe -> new Player(pe.getName(), pe.getSkill(), pe.getPosition(), pe.isHasPlayed()))
                .toList();

        teamA.setGoalie(goalieA);
        teamA.setPlayers(fieldPlayersA);
        teamA.setSubstitutes(substitutesA);

        // Asignar titulares, portero y suplentes para teamB
        List<PlayerEntity> allB = teamBEntity.getPlayers();
        PlayerEntity goalieBEntity = allB.stream()
                .filter(p -> p.getPosition() == Position.GOALKEEPER && p.isHasPlayed())
                .findFirst()
                .orElse(null);
        List<PlayerEntity> fieldPlayersBEntities = allB.stream()
                .filter(p -> p.getPosition() != Position.GOALKEEPER && p.isHasPlayed())
                .toList();
        List<PlayerEntity> substitutesBEntities = allB.stream()
                .filter(p -> !p.isHasPlayed())
                .toList();

        Player goalieB = null;
        if (goalieBEntity != null) {
            goalieB = new Player(
                goalieBEntity.getName(),
                goalieBEntity.getSkill(),
                goalieBEntity.getPosition(),
                goalieBEntity.isHasPlayed()
            );
        }
        List<Player> fieldPlayersB = fieldPlayersBEntities.stream()
                .map(pe -> new Player(pe.getName(), pe.getSkill(), pe.getPosition(), pe.isHasPlayed()))
                .toList();
        List<Player> substitutesB = substitutesBEntities.stream()
                .map(pe -> new Player(pe.getName(), pe.getSkill(), pe.getPosition(), pe.isHasPlayed()))
                .toList();

        teamB.setGoalie(goalieB);
        teamB.setPlayers(fieldPlayersB);
        teamB.setSubstitutes(substitutesB);

        // Validaciones
        if (teamA.getPlayers().size() != 10 || teamA.getGoalie() == null) {
            return ResponseEntity.badRequest().body("El equipo A debe tener 10 jugadores de campo titulares y 1 portero titular.");
        }
        if (teamB.getPlayers().size() != 10 || teamB.getGoalie() == null) {
            return ResponseEntity.badRequest().body("El equipo B debe tener 10 jugadores de campo titulares y 1 portero titular.");
        }
        if (teamA.getSubstitutes().size() < 3) {
            return ResponseEntity.badRequest().body("El equipo A debe tener al menos 3 suplentes.");
        }
        if (teamB.getSubstitutes().size() < 3) {
            return ResponseEntity.badRequest().body("El equipo B debe tener al menos 3 suplentes.");
        }
        
        long defensasA = teamA.getPlayers().stream().filter(p -> p.getPosition() == Position.DEFENDER).count();
        long mediocentrosA = teamA.getPlayers().stream().filter(p -> p.getPosition() == Position.MIDFIELDER).count();
        long delanterosA = teamA.getPlayers().stream().filter(p -> p.getPosition() == Position.FORWARD).count();

        if (defensasA < 2) {
            return ResponseEntity.badRequest().body("El equipo A debe tener al menos 2 defensas titulares.");
        }
        if (mediocentrosA < 2) {
            return ResponseEntity.badRequest().body("El equipo A debe tener al menos 2 mediocentros titulares.");
        }
        if (delanterosA < 1) {
            return ResponseEntity.badRequest().body("El equipo A debe tener al menos 1 delantero titular.");
        }

        // Validación de posiciones titulares para teamB
        long defensasB = teamB.getPlayers().stream().filter(p -> p.getPosition() == Position.DEFENDER).count();
        long mediocentrosB = teamB.getPlayers().stream().filter(p -> p.getPosition() == Position.MIDFIELDER).count();
        long delanterosB = teamB.getPlayers().stream().filter(p -> p.getPosition() == Position.FORWARD).count();

        if (defensasB < 2) {
            return ResponseEntity.badRequest().body("El equipo B debe tener al menos 2 defensas titulares.");
        }
        if (mediocentrosB < 2) {
            return ResponseEntity.badRequest().body("El equipo B debe tener al menos 2 mediocentros titulares.");
        }
        if (delanterosB < 1) {
            return ResponseEntity.badRequest().body("El equipo B debe tener al menos 1 delantero titular.");
        }

        // Asignar lanzadores por nombre
        teamA.setFreekickKickers(
            teamA.getPlayers().stream().filter(p -> localFaltas.contains(p.getName())).toList()
        );
        teamA.setCornerKickers(
            teamA.getPlayers().stream().filter(p -> localCorners.contains(p.getName())).toList()
        );
        teamA.setPenaltyKickers(
            teamA.getPlayers().stream().filter(p -> localPenalty.contains(p.getName())).toList()
        );
        teamB.setFreekickKickers(
            teamB.getPlayers().stream().filter(p -> visitanteFaltas.contains(p.getName())).toList()
        );
        teamB.setCornerKickers(
            teamB.getPlayers().stream().filter(p -> visitanteCorners.contains(p.getName())).toList()
        );
        teamB.setPenaltyKickers(
            teamB.getPlayers().stream().filter(p -> visitantePenalty.contains(p.getName())).toList()
        );

        Game game = new Game(teamA, teamB);
        game.simulate();
        List<String> gameEvents = game.showEvents();

        // Unir todos los eventos en un solo String con saltos de línea HTML
        String resultado = String.join("<br>", gameEvents);

        // Opcional: limpiar los jugadores de los equipos si quieres "borrarlos"
        teamA.setPlayers(List.of());
        teamA.setSubstitutes(List.of());
        teamA.setGoalie(null);
        teamB.setPlayers(List.of());
        teamB.setSubstitutes(List.of());
        teamB.setGoalie(null);

        return ResponseEntity.ok(resultado);
    }




    @PatchMapping(value = "/{teamId}", consumes = {"multipart/form-data"})
    public TeamResponse patchTeam(
            @PathVariable Long teamId,
            @PathVariable Long userId,
            @RequestParam("name") String name,
            @RequestParam(value = "image", required = false) MultipartFile image) throws Exception {

        TeamEntity team = teamService.getById(teamId);
        String oldName = team.getName();
        String safeOldName = oldName.replaceAll("[^a-zA-Z0-9\\-_]", "_");
        String safeNewName = name.replaceAll("[^a-zA-Z0-9\\-_]", "_");
        String imagesDir = "images/" + userId;
        Path userDir = Paths.get(imagesDir);

        String newProfilePictureUrl = team.getProfilePictureUrl();

        // Si cambia el nombre, renombra la imagen si existe
        if (!oldName.equals(name)) {
            Path oldImagePath = userDir.resolve(safeOldName + ".jpg");
            Path newImagePath = userDir.resolve(safeNewName + ".jpg");
            if (Files.exists(oldImagePath)) {
                Files.move(oldImagePath, newImagePath);
                newProfilePictureUrl = "/images/" + userId + "/" + safeNewName + ".jpg";
            }
        }

        // Si hay nueva imagen, reemplaza la imagen (con el nombre nuevo)
        if (image != null && !image.isEmpty()) {
            Path imagePath = userDir.resolve(safeNewName + ".jpg");
            Files.write(imagePath, image.getBytes());
            newProfilePictureUrl = "/images/" + userId + "/" + safeNewName + ".jpg";
        }

        // Actualiza entidad usando tu servicio (solo una vez)
        TeamEntity patchedTeam = teamService.patchTeam(userId, teamId, name, newProfilePictureUrl);

        return TeamResponse.builder()
                .id(patchedTeam.getId())
                .name(patchedTeam.getName())
                .profilePictureUrl(patchedTeam.getProfilePictureUrl())
                .build();
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public TeamResponse createTeam(
            @PathVariable Long userId,
            @RequestParam("name") String name,
            @RequestParam("image") MultipartFile image) throws Exception {

        // 1. Guardar la imagen en /images/{userId}/{teamName}.jpg
        String safeTeamName = name.replaceAll("[^a-zA-Z0-9\\-_]", "_"); // Evita caracteres problemáticos
        String imagesDir = "images/" + userId;
        Path userDir = Paths.get(imagesDir);
        if (!Files.exists(userDir)) {
            Files.createDirectories(userDir);
        }
        String fileName = safeTeamName + ".jpg";
        Path imagePath = userDir.resolve(fileName);
        Files.write(imagePath, image.getBytes());

        // 2. Crear el equipo con la ruta de la imagen
        String relativePath = "/images/" + userId + "/" + fileName;
        TeamEntity createdTeam = teamService.createTeam(userId, name, relativePath);

        return TeamResponse.builder()
                .id(createdTeam.getId())
                .name(createdTeam.getName())
                .profilePictureUrl(relativePath)
                .build();
    }

    @DeleteMapping("/{teamId}")
    public TeamResponse deleteTeam(@PathVariable Long userId , @PathVariable Long teamId){
        TeamEntity deletedTeam = teamService.deleteTeam(teamId , userId);

        // Eliminar la imagen asociada si existe
        String profilePictureUrl = deletedTeam.getProfilePictureUrl();
        if (profilePictureUrl != null && !profilePictureUrl.isBlank()) {
            try {
                String relativePath = profilePictureUrl.startsWith("/") ? profilePictureUrl.substring(1) : profilePictureUrl;
                Path imagePath = Paths.get(relativePath);
                if (Files.exists(imagePath)) {
                    Files.delete(imagePath);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return TeamResponse.builder()
                .id(deletedTeam.getId())
                .name(deletedTeam.getName())
                .profilePictureUrl(deletedTeam.getProfilePictureUrl())
                .build();

    }

    @GetMapping
    public List<TeamResponse> getTeams(@PathVariable Long userId, @RequestParam(required = false) String name) {
        List<TeamEntity> teams;

        if (name != null && !name.isBlank()) {
            teams = teamService.getByName(userId, name);
        } else {
            teams = teamService.getAllTeams(userId);
        }

        return teams.stream()
                .map(team -> TeamResponse.builder()
                        .id(team.getId())
                        .name(team.getName())
                        .profilePictureUrl(team.getProfilePictureUrl())
                        .build())
                .toList();
    }

    @GetMapping("/images/{teamUserId}/{imageName:.+}")
    public ResponseEntity<Resource> getTeamImage(
            @PathVariable("teamUserId") String teamUserId,
            @PathVariable String imageName) throws Exception {
        Path imagePath = Paths.get("images", teamUserId, imageName);
        Resource resource = new UrlResource(imagePath.toUri());
        if (resource.exists() && resource.isReadable()) {
            return ResponseEntity.ok().body(resource);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
