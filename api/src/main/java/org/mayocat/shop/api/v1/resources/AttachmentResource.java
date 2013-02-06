package org.mayocat.shop.api.v1.resources;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.mayocat.shop.api.v1.parameters.ImageOptions;
import org.mayocat.shop.image.ImageService;
import org.mayocat.shop.model.Attachment;
import org.mayocat.shop.rest.annotation.ExistingTenant;
import org.mayocat.shop.rest.resources.Resource;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;

import com.google.common.base.Optional;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

/**
 * @version $Id$
 */
@Component("/api/1.0/attachment")
@Path("/api/1.0/attachment")
@ExistingTenant
public class AttachmentResource extends AbstractAttachmentResource implements Resource
{
    @Inject
    private ImageService imageService;

    @Inject
    private Logger logger;

    private static final List<String> IMAGE_EXTENSIONS = new ArrayList<String>();

    static {
        IMAGE_EXTENSIONS.add("jpg");
        IMAGE_EXTENSIONS.add("jpeg");
        IMAGE_EXTENSIONS.add("gif");
        IMAGE_EXTENSIONS.add("png");
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response addAttachment(@FormDataParam("file") InputStream uploadedInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail,
            @FormDataParam("title") String title)
    {
        return this.addAttachment(uploadedInputStream, fileDetail.getFileName(), title, null);
    }

    @GET
    @Path("/{slug}.{ext}")
    public Response downloadFile(@PathParam("slug") String slug, @PathParam("ext") String extension,
            @Context ServletContext servletContext, @Context Optional<ImageOptions> imageOptions)
    {
        String fileName = slug + "." + extension;
        Attachment file = this.getAttachmentStore().findBySlugAndExtension(slug, extension);
        if (file == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (imageOptions.isPresent()) {
            if (!IMAGE_EXTENSIONS.contains(extension)) {
                // Refuse to treat a request with image options for a non-image attachment
                return Response.status(Response.Status.BAD_REQUEST).entity("Image options are not supported for non-" +
                        "image attachments").build();
            } else {
                try {
                    Image image = imageService.readImage(file.getData());
                    Optional<Dimension> newDimension = imageService.newDimension(image,
                            imageOptions.get().getWidth(),
                            imageOptions.get().getHeight());

                    if (newDimension.isPresent()) {
                        return Response.ok(imageService.scaleImage(image, newDimension.get()),
                                servletContext.getMimeType(fileName))
                                .header("Content-disposition", "inline; filename*=utf-8''" + fileName)
                                .build();
                    }

                    return Response.ok(image, servletContext.getMimeType(fileName))
                            .header("Content-disposition", "inline; filename*=utf-8''" + fileName)
                            .build();


                } catch (IOException e) {
                    this.logger.warn("Failed to scale image for attachment [{slug}]", slug);
                    return Response.serverError().entity("Failed to scale image").build();
                }
            }
        }

        return Response.ok(file.getData(), servletContext.getMimeType(fileName))
                .header("Content-disposition", "inline; filename*=utf-8''" + fileName)
                .build();
    }
}
