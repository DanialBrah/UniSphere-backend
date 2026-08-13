package com.unisphere.backend.projects;

import com.unisphere.backend.identity.dto.RegisterEmployerRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectControllerTest extends AbstractProjectIntegrationTest {

    @Test
    void createProject_asStudent_succeeds() throws Exception {
        String token = registerStudentAndGetToken("owner.create@test.com", "PC0001");

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"title\":\"Campus App\",\"description\":\"A test project\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Campus App"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.canModify").value(true))
                .andExpect(jsonPath("$.data.memberCount").value(1));
    }

    @Test
    void createProject_asEmployer_isForbidden() throws Exception {
        String token = registerEmployerAndGetToken();

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"title\":\"Should fail\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProject_byNonOwner_isForbidden() throws Exception {
        String ownerToken = registerStudentAndGetToken("owner.update@test.com", "PC0002");
        Long projectId = createProject(ownerToken, "Owned Project");
        String otherToken = registerStudentAndGetToken("other.update@test.com", "PC0003");

        mockMvc.perform(put(BASE + "/{projectId}", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + otherToken)
                        .content("{\"title\":\"Hijacked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusTransition_openToInProgressToCompleted_succeeds_andCompletedIsTerminal() throws Exception {
        String token = registerStudentAndGetToken("owner.status@test.com", "PC0004");
        Long projectId = createProject(token, "Status Project");

        mockMvc.perform(patch(BASE + "/{projectId}/status", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        mockMvc.perform(patch(BASE + "/{projectId}/status", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.isRecruiting").value(false));

        mockMvc.perform(patch(BASE + "/{projectId}/status", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_PROJECT_STATUS_TRANSITION"));
    }

    @Test
    void deleteProject_withNoOtherMembers_succeeds() throws Exception {
        String token = registerStudentAndGetToken("owner.delete@test.com", "PC0005");
        Long projectId = createProject(token, "Deletable Project");

        mockMvc.perform(delete(BASE + "/{projectId}", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/{projectId}", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void addRole_andListRoles_returnsTheRole() throws Exception {
        String token = registerStudentAndGetToken("owner.roles@test.com", "PC0006");
        Long projectId = createProject(token, "Roled Project");
        addRole(token, projectId, "Backend Developer", 2);

        mockMvc.perform(get(BASE + "/{projectId}/roles", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Backend Developer"))
                .andExpect(jsonPath("$.data[0].slots").value(2))
                .andExpect(jsonPath("$.data[0].filledCount").value(0))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"));
    }

    @Test
    void deleteRole_withNoApplications_succeeds() throws Exception {
        String token = registerStudentAndGetToken("owner.roledelete@test.com", "PC0007");
        Long projectId = createProject(token, "Role Delete Project");
        Long roleId = addRole(token, projectId, "Designer", 1);

        mockMvc.perform(delete(BASE + "/{projectId}/roles/{roleId}", projectId, roleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void browseFeed_returnsCreatedProject() throws Exception {
        String token = registerStudentAndGetToken("owner.feed@test.com", "PC0008");
        createProject(token, "Feed Project");

        mockMvc.perform(get(BASE).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void search_findsProjectByTitle() throws Exception {
        String token = registerStudentAndGetToken("owner.search@test.com", "PC0009");
        createProject(token, "UniqueSearchableTitleXyz");

        mockMvc.perform(get(BASE + "/search").param("q", "UniqueSearchableTitleXyz")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("UniqueSearchableTitleXyz"));
    }

    private String registerEmployerAndGetToken() throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/register/employer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterEmployerRequest("employer.projects@test.com", PASSWORD, "Test Co",
                                        null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result, "/data/accessToken");
    }
}
