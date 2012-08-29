/*
 * Copyright 2010, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.rest.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.zanata.adapter.FileFormatAdapter;
import org.zanata.adapter.po.PoWriter2;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.dao.DocumentDAO;
import org.zanata.model.HDocument;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;
import org.zanata.service.FileSystemService;
import org.zanata.service.TranslationFileService;
import org.zanata.service.FileSystemService.DownloadDescriptorProperties;

@Name("fileService")
@Path(FileResource.FILE_RESOURCE)
@Produces( { MediaType.APPLICATION_OCTET_STREAM })
@Consumes( { MediaType.APPLICATION_OCTET_STREAM })
public class FileService implements FileResource
{
   public static final String RAW_DOWNLOAD_TEMPLATE = "/raw/{projectSlug}/{iterationSlug}";

   private static final String SUBSTITUTE_APPROVED_AND_FUZZY = "half-baked";
   private static final String SUBSTITUTE_APPROVED = "baked";

   @In
   private DocumentDAO documentDAO;
   
   @In(create=true)
   private TranslatedDocResourceService translatedDocResourceService;
   
   @In
   private FileSystemService fileSystemServiceImpl;

   @In
   private TranslationFileService translationFileServiceImpl;

   @In
   private ResourceUtils resourceUtils;

   @GET
   @Path(RAW_DOWNLOAD_TEMPLATE)
   // /file/raw/{projectSlug}/{iterationSlug}?docId={docId}
   public Response downloadRawFile( @PathParam("projectSlug") String projectSlug,
                                    @PathParam("iterationSlug") String iterationSlug,
                                    @QueryParam("docId") String docId)
   {
      final Response response;
      HDocument document = this.documentDAO.getByProjectIterationAndDocId(projectSlug, iterationSlug, docId);

      if( document != null && translationFileServiceImpl.hasPersistedDocument(projectSlug, iterationSlug, document.getPath(), document.getName()))
      {
         InputStream fileContents = translationFileServiceImpl.streamDocument(projectSlug, iterationSlug, document.getPath(), document.getName());
         StreamingOutput output = new InputStreamStreamingOutput(fileContents);
         response = Response.ok()
               .header("Content-Disposition", "attachment; filename=\"" + document.getName() + "\"")
               .entity(output).build();
      }
      else
      {
         response = Response.status(Status.NOT_FOUND).build();
      }
      return response;
   }

   /**
    * Downloads a single translation file.
    * 
    * @param projectSlug Project identifier.
    * @param iterationSlug Project iteration identifier.
    * @param locale Translations for this locale will be contained in the downloaded document.
    * @param fileExtension File type to be downloaded. (Options: 'po')
    * @param docId Document identifier to fetch translations for.
    * @return The following response status codes will be returned from this operation:<br>
    * OK(200) - A translation file in the requested format with translations for the requested document in a
    * project, iteration and locale. <br>
    * NOT FOUND(404) - If a document is not found with the given parameters.<br>
    * INTERNAL SERVER ERROR(500) - If there is an unexpected error in the server while performing this operation.
    */
   @Override
   @GET
   @Path(FILE_DOWNLOAD_TEMPLATE)
   // /file/translation/{projectSlug}/{iterationSlug}/{locale}/{fileType}?docId={docId}
   public Response downloadTranslationFile( @PathParam("projectSlug") String projectSlug,
                                            @PathParam("iterationSlug") String iterationSlug,
                                            @PathParam("locale") String locale,
                                            @PathParam("fileType") String fileExtension,
                                            @QueryParam("docId") String docId )
   {
      final Response response;
      HDocument document = this.documentDAO.getByProjectIterationAndDocId(projectSlug, iterationSlug, docId);

      if( document == null )
      {
         response = Response.status(Status.NOT_FOUND).build();
      }
      else if ("po".equals(fileExtension))
      {
         final Set<String> extensions = new HashSet<String>();
         extensions.add("gettext");
         extensions.add("comment");

         // Perform translation of Hibernate DTOs to JAXB DTOs
         TranslationsResource transRes = 
               (TranslationsResource) this.translatedDocResourceService.getTranslations(docId, new LocaleId(locale), extensions, true).getEntity();
         Resource res = this.resourceUtils.buildResource(document);

         StreamingOutput output = new POStreamingOutput(res, transRes);
         response = Response.ok()
               .header("Content-Disposition", "attachment; filename=\"" + document.getName() + ".po\"")
               .entity(output).build();
      }
      else if ( (SUBSTITUTE_APPROVED.equals(fileExtension) || SUBSTITUTE_APPROVED_AND_FUZZY.equals(fileExtension))
            && translationFileServiceImpl.hasPersistedDocument(projectSlug, iterationSlug, document.getPath(), document.getName()))
      {
         final Set<String> extensions = Collections.<String>emptySet();
         TranslationsResource transRes = 
               (TranslationsResource) this.translatedDocResourceService.getTranslations(docId, new LocaleId(locale), extensions, true).getEntity();
         // Filter to only provide translated targets. "Preview" downloads include fuzzy.
         // Using new list is used as transRes list appears not to be a modifiable implementation.
         Map<String, TextFlowTarget> translations = new HashMap<String, TextFlowTarget>();
         boolean useFuzzy = SUBSTITUTE_APPROVED_AND_FUZZY.equals(fileExtension);
         for (TextFlowTarget target : transRes.getTextFlowTargets())
         {
            if (target.getState() == ContentState.Approved || (useFuzzy && target.getState() == ContentState.NeedReview))
            {
               translations.put(target.getResId(), target);
            }
         }

         URI uri = translationFileServiceImpl.getDocumentURI(projectSlug, iterationSlug, document.getPath(), document.getName());
         StreamingOutput output = new FormatAdapterStreamingOutput(uri, translations, locale, translationFileServiceImpl.getAdapterFor(docId));
         response = Response.ok()
               .header("Content-Disposition", "attachment; filename=\"" + document.getName() + "\"")
               .entity(output).build();
      }
      else
      {
         response = Response.status(Status.UNSUPPORTED_MEDIA_TYPE).build();
      }

      return response;
   }

   /**
    * Downloads a previously generated file.
    * 
    * @param downloadId The Zanata generated download id.
    * @return The following response status codes will be returned from this operation:<br>
    * OK(200) - A translation file in the requested format with translations for the requested document in a
    * project, iteration and locale. <br>
    * NOT FOUND(404) - If a downloadable file is not found for the given id, or is not yet ready for download 
    * (i.e. the system is still preparing it).<br>
    * INTERNAL SERVER ERROR(500) - If there is an unexpected error in the server while performing this operation.
    */
   @Override
   @GET
   @Path(DOWNLOAD_TEMPLATE)
   // /file/download/{downloadId}
   public Response download( @PathParam("downloadId") String downloadId )
   {
      try
      {
         // Check that the download exists by looking at the download descriptor
         Properties descriptorProps = this.fileSystemServiceImpl.findDownloadDescriptorProperties(downloadId);
         
         if( descriptorProps == null )
         {
            return Response.status(Status.NOT_FOUND).build(); 
         }
         else
         {
            File toDownload = this.fileSystemServiceImpl.findDownloadFile(downloadId);
            
            if( toDownload == null )
            {
               return Response.status(Status.NOT_FOUND).build(); 
            }
            else
            {
               return Response.ok()
                     .header("Content-Disposition", "attachment; filename=\"" 
                           + descriptorProps.getProperty(DownloadDescriptorProperties.DownloadFileName.toString()) 
                           + "\"")
                     .header("Content-Length", toDownload.length())
                     .entity( new FileStreamingOutput(toDownload) )
                     .build();
            }
         }
      }
      catch (IOException e)
      {
         return Response.serverError().status( Status.INTERNAL_SERVER_ERROR ).build();
      }
   }


   /*
    * Private class that implements PO file streaming of a document.
    */
   private class POStreamingOutput implements StreamingOutput
   {
      private Resource resource;
      private TranslationsResource transRes;

      public POStreamingOutput( Resource resource, TranslationsResource transRes )
      {
         this.resource = resource;
         this.transRes = transRes;
      }

      @Override
      public void write(OutputStream output) throws IOException, WebApplicationException
      {         
         PoWriter2 writer = new PoWriter2();
         writer.writePo(output, "UTF-8", this.resource, this.transRes);
      }
   }

   private class InputStreamStreamingOutput implements StreamingOutput
   {
      private InputStream input;

      public InputStreamStreamingOutput(InputStream input)
      {
         this.input = input;
      }

      @Override
      public void write(OutputStream output) throws IOException, WebApplicationException
      {
         byte[] buffer = new byte[4096]; // To hold file contents
         int bytesRead; // How many bytes in buffer

         while ((bytesRead = input.read(buffer)) != -1)
         {
            output.write(buffer, 0, bytesRead);
         }
      }
   }

   private class FormatAdapterStreamingOutput implements StreamingOutput
   {
      private Map<String, TextFlowTarget> translations;
      private String locale;
      private URI original;
      private FileFormatAdapter adapter;

      public FormatAdapterStreamingOutput(URI originalDoc, Map<String, TextFlowTarget> translations, String locale, FileFormatAdapter adapter)
      {
         this.translations = translations;
         this.locale = locale;
         this.original = originalDoc;
         this.adapter = adapter;
      }

      @Override
      public void write(OutputStream output) throws IOException, WebApplicationException
      {
         adapter.writeTranslatedFile(output, original, translations, locale);
      }
   }

   /*
    * Private class that implements downloading from a previously prepared file. 
    */
   private class FileStreamingOutput implements StreamingOutput
   {
      private File file;
      
      public FileStreamingOutput( File file )
      {
         this.file = file;
      }

      @Override
      public void write(OutputStream output) throws IOException, WebApplicationException
      {
         FileInputStream input = new FileInputStream(this.file);
         byte[] buffer = new byte[4096]; // To hold file contents
         int bytesRead; // How many bytes in buffer

         while ((bytesRead = input.read(buffer)) != -1)
         {
            output.write(buffer, 0, bytesRead);
         }
      }
   }
}
