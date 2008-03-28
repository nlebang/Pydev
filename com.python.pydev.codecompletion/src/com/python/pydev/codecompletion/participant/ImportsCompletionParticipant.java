/*
 * Created on 21/08/2005
 */
package com.python.pydev.codecompletion.participant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.python.pydev.core.FullRepIterable;
import org.python.pydev.core.ICodeCompletionASTManager;
import org.python.pydev.core.ICompletionState;
import org.python.pydev.core.ILocalScope;
import org.python.pydev.core.IModulesManager;
import org.python.pydev.core.IPythonNature;
import org.python.pydev.core.IToken;
import org.python.pydev.core.docutils.PySelection.ActivationTokenAndQual;
import org.python.pydev.dltk.console.ui.IScriptConsoleViewer;
import org.python.pydev.editor.codecompletion.CompletionRequest;
import org.python.pydev.editor.codecompletion.IPyCompletionProposal;
import org.python.pydev.editor.codecompletion.IPyDevCompletionParticipant;
import org.python.pydev.editor.codecompletion.IPyDevCompletionParticipant2;
import org.python.pydev.editor.codecompletion.PyCodeCompletionImages;

import com.python.pydev.analysis.CtxInsensitiveImportComplProposal;
import com.python.pydev.analysis.ui.AutoImportsPreferencesPage;
import com.python.pydev.codecompletion.ctxinsensitive.PyConsoleCompletion;

/**
 * Gathers completions from the modules available (for the editor or for the console).
 *
 * @author Fabio
 */
public class ImportsCompletionParticipant implements IPyDevCompletionParticipant, IPyDevCompletionParticipant2{

    // Console completions ---------------------------------------------------------------------------------------------
    
    public Collection<ICompletionProposal> computeConsoleCompletions(ActivationTokenAndQual tokenAndQual,
            List<IPythonNature> naturesUsed, IScriptConsoleViewer viewer, int requestOffset) {
        ArrayList<ICompletionProposal> completions = new ArrayList<ICompletionProposal>();
        
        if(tokenAndQual.activationToken != null && tokenAndQual.activationToken.length() > 0){
            //we only want 
            return completions;
        }

        String qual = tokenAndQual.qualifier;
        if(qual.length() >= 2 && naturesUsed.size() > 0){ //at least n characters required...
            
            int qlen = qual.length();
            boolean addAutoImport = AutoImportsPreferencesPage.doAutoImport();
            
            for(IPythonNature nature : naturesUsed){
                fillCompletions(requestOffset, completions, qual, nature, qlen, addAutoImport, viewer, false);
            }
            
            fillCompletions(requestOffset, completions, qual, naturesUsed.get(0), qlen, addAutoImport, viewer, true);
            
        }
        return completions;
    }


    private void fillCompletions(int requestOffset, ArrayList<ICompletionProposal> completions, String qual,
            IPythonNature nature, int qlen, boolean addAutoImport, IScriptConsoleViewer viewer, boolean getSystem) {
        
        ICodeCompletionASTManager astManager = nature.getAstManager();
        
        Image img = PyCodeCompletionImages.getImageForType(IToken.TYPE_PACKAGE);
        
        IModulesManager modulesManager = astManager.getModulesManager();
        if(getSystem){
            modulesManager = modulesManager.getSystemModulesManager();
        }
        
        String lowerQual = qual.toLowerCase();
        Set<String> allModuleNames = modulesManager.getAllModuleNames(false, lowerQual);

        StringBuffer realImportRep=new StringBuffer();
        HashSet<String> alreadyFound = new HashSet<String>();
        
        for (String name:allModuleNames) {
            
            FullRepIterable iterable = new FullRepIterable(name);
            for (String string : iterable) {
                //clear the buffer...
                realImportRep.delete(0, realImportRep.length());
                
                String[] strings = FullRepIterable.headAndTail(string);
                String importRep = strings[1];
                String lowerImportRep = importRep.toLowerCase();
                if(!lowerImportRep.startsWith(lowerQual)){
                    continue;
                }

                StringBuffer displayString = new StringBuffer(importRep);
                
                String packageName = strings[0];
                if(addAutoImport){
                    realImportRep.append("import ");
                    realImportRep.append(strings[1]);
                }
                
                if(packageName.length() > 0){
                    if(addAutoImport){
                        realImportRep.insert(0, " ");
                        realImportRep.insert(0, packageName);
                        realImportRep.insert(0, "from ");
                    }
                    displayString.append(" - ");
                    displayString.append(packageName);
                }
                
                String found = displayString.toString();
                if(alreadyFound.contains(found)){
                    continue;
                }
                alreadyFound.add(found);
                
                PyConsoleCompletion  proposal = new PyConsoleCompletion (
                        importRep,
                        requestOffset - qlen, 
                        qlen, 
                        realImportRep.length(), 
                        img, 
                        found, 
                        (IContextInformation)null, 
                        "", 
                        lowerImportRep.equals(lowerQual)? IPyCompletionProposal.PRIORITY_LOCALS_2 : IPyCompletionProposal.PRIORITY_PACKAGES,
                        realImportRep.toString(), viewer);

                completions.add(proposal);
            }
        }
    }


    // Editor completions ----------------------------------------------------------------------------------------------
    
    private Collection<CtxInsensitiveImportComplProposal> getThem(CompletionRequest request, 
            ICompletionState state, boolean addAutoImport) {
        ArrayList<CtxInsensitiveImportComplProposal> list = new ArrayList<CtxInsensitiveImportComplProposal>();
        if(request.isInCalltip){
            return list;
        }
        
        if(request.qualifier.length() >= 2){ //at least n characters required...
            
            ICodeCompletionASTManager astManager = request.nature.getAstManager();
            String initialModule = request.resolveModule();
            
            Image img = PyCodeCompletionImages.getImageForType(IToken.TYPE_PACKAGE);
            
            IModulesManager projectModulesManager = astManager.getModulesManager();
            
            String lowerQual = request.qualifier.toLowerCase();
            Set<String> allModuleNames = projectModulesManager.getAllModuleNames(true, lowerQual);

            StringBuffer realImportRep=new StringBuffer();
            HashSet<String> importedNames = getImportedNames(state);
            
            for (String name:allModuleNames) {
                if(name.equals(initialModule)){
                    continue;
                }
                
                FullRepIterable iterable = new FullRepIterable(name);
                for (String string : iterable) {
                    //clear the buffer...
                    realImportRep.delete(0, realImportRep.length());
                    
                    String[] strings = FullRepIterable.headAndTail(string);
                    String importRep = strings[1];
                    String lowerImportRep = importRep.toLowerCase();
                    if(!lowerImportRep.startsWith(lowerQual) || importedNames.contains(importRep)){
                        continue;
                    }

                    StringBuffer displayString = new StringBuffer(importRep);
                    
                    String packageName = strings[0];
                    if(addAutoImport){
                        realImportRep.append("import ");
                        realImportRep.append(strings[1]);
                    }
                    
                    if(packageName.length() > 0){
                        if(addAutoImport){
                            realImportRep.insert(0, " ");
                            realImportRep.insert(0, packageName);
                            realImportRep.insert(0, "from ");
                        }
                        displayString.append(" - ");
                        displayString.append(packageName);
                    }
                    
                    CtxInsensitiveImportComplProposal  proposal = new CtxInsensitiveImportComplProposal (
                            importRep,
                            request.documentOffset - request.qlen, 
                            request.qlen, 
                            realImportRep.length(), 
                            img, 
                            displayString.toString(), 
                            (IContextInformation)null, 
                            "", 
                            lowerImportRep.equals(lowerQual)? IPyCompletionProposal.PRIORITY_LOCALS_2 : IPyCompletionProposal.PRIORITY_PACKAGES,
                            realImportRep.toString());

                    list.add(proposal);
                }
            }
        }
        return list;
    }

    private HashSet<String> getImportedNames(ICompletionState state) {
        List<IToken> tokenImportedModules = state.getTokenImportedModules();
        HashSet<String> importedNames = new HashSet<String>();
        if(tokenImportedModules != null){
            for (IToken token : tokenImportedModules) {
                importedNames.add(token.getRepresentation());
            }
        }
        return importedNames;
    }

    @SuppressWarnings("unchecked")
    public Collection getGlobalCompletions(CompletionRequest request, ICompletionState state) {
        return getThem(request, state, AutoImportsPreferencesPage.doAutoImport());
    }
    
    @SuppressWarnings("unchecked")
    public Collection getArgsCompletion(ICompletionState state, ILocalScope localScope, Collection<IToken> interfaceForLocal) {
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public Collection getStringGlobalCompletions(CompletionRequest request, ICompletionState state) {
        return getThem(request, state, false);
    }

}
