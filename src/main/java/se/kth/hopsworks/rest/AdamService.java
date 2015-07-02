package se.kth.hopsworks.rest;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import se.kth.bbc.jobs.adam.AdamCommandDTO;
import se.kth.bbc.jobs.adam.AdamJobConfiguration;
import se.kth.bbc.jobs.jobhistory.JobHistory;
import se.kth.hopsworks.controller.AdamController;
import se.kth.hopsworks.filters.AllowedRoles;

/**
 *
 * @author stig
 */
@RequestScoped
public class AdamService {

  private static final Logger logger = Logger.getLogger(AdamService.class.
          getName());

  @EJB
  private AdamController adamController;

  private Integer projectId;

  AdamService setProjectId(Integer id) {
    this.projectId = id;
    return this;
  }

  /**
   * Get a list of the available Adam commands.
   * <p>
   * @param sc
   * @param req
   * @return
   */
  @GET
  @Path("/commands")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public Response getAdamCommands(@Context SecurityContext sc,
          @Context HttpServletRequest req) {
    AdamCommandDTO[] commands = AdamCommandDTO.values();
    return Response.ok(commands).build();
  }

  /**
   * Run an ADAM job. Accepts a JSONized AdamJobConfiguration, which contains
   * the selected command along with the set parameters.
   * <p>
   * @param config
   * @param sc
   * @param req
   * @return
   * @throws AppException
   */
  @POST
  @Path("/run")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @AllowedRoles(roles = AllowedRoles.ALL)
  public Response run(AdamJobConfiguration config, @Context SecurityContext sc,
          @Context HttpServletRequest req) throws AppException {
    if (config == null) {
      throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(),
              "You must set a job configuration first.");
    }
    try {
      JobHistory jh = adamController.startJob(config, req.getUserPrincipal().
              getName(), projectId);
      return Response.ok(jh).build();
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "Error running ADAM job.", ex);
      throw new AppException(Response.Status.INTERNAL_SERVER_ERROR.
              getStatusCode(), "Error running job: " + ex.getLocalizedMessage());
    }
  }
}
