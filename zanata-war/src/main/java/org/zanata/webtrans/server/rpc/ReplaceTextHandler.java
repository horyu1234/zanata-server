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
package org.zanata.webtrans.server.rpc;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.webtrans.server.ActionHandlerFor;
import org.zanata.webtrans.shared.model.TransUnitUpdateRequest;
import org.zanata.webtrans.shared.rpc.ReplaceText;
import org.zanata.webtrans.shared.rpc.UpdateTransUnitResult;
import com.google.common.base.Strings;

import net.customware.gwt.dispatch.server.ExecutionContext;
import net.customware.gwt.dispatch.shared.ActionException;

@Name("webtrans.gwt.ReplaceTextHandler")
@Scope(ScopeType.STATELESS)
@ActionHandlerFor(ReplaceText.class)
public class ReplaceTextHandler extends AbstractActionHandler<ReplaceText, UpdateTransUnitResult>
{
   private static final Logger LOGGER = LoggerFactory.getLogger(ReplaceTextHandler.class);

   @In(value = "webtrans.gwt.UpdateTransUnitHandler", create = true)
   UpdateTransUnitHandler updateTransUnitHandler;

   @Override
   public UpdateTransUnitResult execute(ReplaceText action, ExecutionContext context) throws ActionException
   {
      //TODO in an optimal world we should do security check before making all the effort. Wait for SecurityService implementation
      String searchText = action.getSearchText();
      String replaceText = action.getReplaceText();
      if (Strings.isNullOrEmpty(searchText) || Strings.isNullOrEmpty(replaceText))
      {
         throw new ActionException("search or replace text is empty");
      }

      int flags = action.isCaseSensitive() ? Pattern.UNICODE_CASE : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
      Pattern pattern = Pattern.compile(Pattern.quote(action.getSearchText()), flags);

      for (TransUnitUpdateRequest request : action.getUpdateRequests())
      {
         List<String> contents = request.getNewContents();
         LOGGER.debug("transUnit {} before replace [{}]", request.getTransUnitId(), contents);
         for (int i = 0; i < contents.size(); i++)
         {
            String content = contents.get(i);
            Matcher matcher = pattern.matcher(content);
            String newContent = matcher.replaceAll(replaceText);
            contents.set(i, newContent);
         }
         LOGGER.debug("transUnit {} after replace [{}]", request.getTransUnitId(), contents);
      }

      return updateTransUnitHandler.execute(action, context);
   }

   @Override
   public void rollback(ReplaceText action, UpdateTransUnitResult result, ExecutionContext context) throws ActionException
   {
      throw new ActionException("not supported");
   }
}
