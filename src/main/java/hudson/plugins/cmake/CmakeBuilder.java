package hudson.plugins.cmake;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes <tt>cmake</tt> as the build process.
 *
 *
 * @author Volker Kaiser
 */
public class CmakeBuilder extends Builder {

	private static final String CMAKE_EXECUTABLE = "CMAKE_EXECUTABLE";

	private static final String CMAKE = "cmake";
	
	final private String sourceDir;
    final private String buildDir;
    final private String installDir;
    final private String buildType;
    final private String otherBuildType;
    final private String generator;
    final private String makeCommand;
    final private String installCommand;
    final private String preloadScript;
    final private String cmakeArgs;
    final private String projectCmakePath;
    final private boolean cleanBuild;
    final private boolean cleanInstallDir;

    private CmakeBuilderImpl builderImpl;

    @DataBoundConstructor
    public CmakeBuilder(String sourceDir, 
    		String buildDir, 
    		String installDir, 
    		String buildType,
    		String otherBuildType,
    		boolean cleanBuild,
    		boolean cleanInstallDir,
    		String generator, 
    		String makeCommand, 
    		String installCommand,
    		String preloadScript,
    		String cmakeArgs, 
    		String projectCmakePath) {
    	this.sourceDir = sourceDir;
		this.buildDir = buildDir;
		this.installDir = installDir;
		this.buildType = buildType;
        this.otherBuildType = otherBuildType;
		this.cleanBuild = cleanBuild;
		this.cleanInstallDir = cleanInstallDir;
		this.generator = generator;
		this.makeCommand = makeCommand;
		this.installCommand = installCommand; 		
		this.cmakeArgs = cmakeArgs;
		this.projectCmakePath = projectCmakePath;
		this.preloadScript = preloadScript;
		builderImpl = new CmakeBuilderImpl();
    }

    public String getSourceDir() {
    	return this.sourceDir;
    }
    
    public String getBuildDir() {
		return this.buildDir;
	}

    public String getInstallDir() {
    	return this.installDir;
    }

    public String getBuildType() {
    	return this.buildType;
    }

    public String getOtherBuildType() {
    	return this.otherBuildType;
    }
    
    public boolean getCleanBuild() {
    	return this.cleanBuild;
    }
    
    public boolean getCleanInstallDir() {
    	return this.cleanInstallDir;
    }
    
    public String getGenerator() {
    	return this.generator;
    }
    
    public String getMakeCommand() {
    	return this.makeCommand;
    }
    
    public String getInstallCommand() {
    	return this.installCommand;
    }
    
    public String getPreloadScript() {
    	return this.preloadScript;
    }
    
    public String getCmakeArgs() {
    	return this.cmakeArgs;
    }
    
    public String getProjectCmakePath() {
    	return this.projectCmakePath;
    }

    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        final EnvVars envs = build.getEnvironment(listener);

        final String eSourceDir = EnvVarReplacer.replace(sourceDir, envs);
        final String eBuildDir = EnvVarReplacer.replace(buildDir, envs);
        final String eInstallDir = EnvVarReplacer.replace(installDir, envs);
        final String eOtherBuildType = EnvVarReplacer.replace(otherBuildType, envs);
        final String eGenerator = EnvVarReplacer.replace(generator, envs);
        final String eMakeCommand = EnvVarReplacer.replace(makeCommand, envs);
        final String eInstallCommand = EnvVarReplacer.replace(installCommand, envs);
        final String ePreloadScript = EnvVarReplacer.replace(preloadScript, envs);
        final String eCmakeArgs = EnvVarReplacer.replace(cmakeArgs, envs);
        final String eProjectCmakePath = EnvVarReplacer.replace(projectCmakePath, envs);

    	listener.getLogger().println("MODULE: " + build.getModuleRoot());
        listener.getLogger().println("Custom CMAKE BUILD PLUGIN");
    	
        listener.getLogger().println("Printing environment variables");
        for (Map.Entry<String, String> entry : envs.entrySet()) {
            listener.getLogger().println("Key: " + entry.getKey() + ". Value: " + entry.getValue());
        }

        final Map<String, String> buildEnvs = build.getBuildVariables();
        listener.getLogger().println("Printing build environment variables");
        for (Map.Entry<String, String> entry : buildEnvs.entrySet()) {
            listener.getLogger().println("Key: " + entry.getKey() + ". Value: " + entry.getValue());
        }

        final FilePath workSpace = build.getWorkspace();

        String theSourceDir;
    	String theInstallDir;
    	String theBuildDir = this.buildDir;
    	try {
    		theBuildDir = prepareBuildDir(eBuildDir, listener, envs, workSpace);
    		theSourceDir = prepareSourceDir(eSourceDir, envs, workSpace);
    		theInstallDir = prepareInstallDir(eInstallDir, listener, envs, workSpace);
    	} catch (IOException ioe) {
    		listener.getLogger().println(ioe.getMessage());
    		return false;
    	}
        String theBuildType = prepareBuildType(eOtherBuildType);

    	listener.getLogger().println("Build   dir  : " + theBuildDir);
    	listener.getLogger().println("Source  dir  : " + theSourceDir);
    	listener.getLogger().println("Install dir  : " + theInstallDir);

    	final CmakeLauncher cmakeLauncher = new CmakeLauncher(launcher, envs, workSpace, listener, theBuildDir);
    	
    	try {
            if (!cmakeLauncher.launchCmake(
                    checkCmake(eProjectCmakePath, build.getBuiltOn(), listener, envs),
                    eGenerator,
                    ePreloadScript,
                    theSourceDir,
                    theInstallDir,
                    theBuildType,
                    eCmakeArgs)) return false;

    		if (!cmakeLauncher.launchMake(eMakeCommand)) {
    			return false;
    		}
    		
    		return cmakeLauncher.launchInstall(theInstallDir, eInstallCommand);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException ie) {
			ie.printStackTrace();
		}
		return false;
    }

	private String prepareBuildType(String eOtherBuildType) {
        if ((eOtherBuildType != null) && (eOtherBuildType.length() > 0)) {
            return eOtherBuildType;
        }
		return buildType;
	}

	private String prepareInstallDir(String eInstallDir, BuildListener listener, EnvVars envs, final FilePath workSpace) throws IOException {
		if (this.cleanInstallDir) {
			listener.getLogger().println("Wiping out install Dir... " + eInstallDir);
			return getCmakeBuilderImpl().preparePath(workSpace, envs, eInstallDir,
					CmakeBuilderImpl.PreparePathOptions.CREATE_NEW_IF_EXISTS);
		} 
		return getCmakeBuilderImpl().preparePath(workSpace, envs, eInstallDir,
				CmakeBuilderImpl.PreparePathOptions.CREATE_IF_NOT_EXISTING);
	}

	private String prepareSourceDir(String eSourceDir, EnvVars envs, final FilePath workSpace)
			throws IOException {
		return getCmakeBuilderImpl().preparePath(workSpace, envs, eSourceDir,
				CmakeBuilderImpl.PreparePathOptions.CHECK_PATH_EXISTS);
	}

	private String prepareBuildDir(String eBuildDir, BuildListener listener, EnvVars envs, final FilePath workSpace)
            throws IOException
    {
		if (this.cleanBuild) {
			listener.getLogger().println("Cleaning build Dir... " + eBuildDir);
			return getCmakeBuilderImpl().preparePath(workSpace, envs, eBuildDir,
					CmakeBuilderImpl.PreparePathOptions.CREATE_NEW_IF_EXISTS);
		}
		return getCmakeBuilderImpl().preparePath(workSpace, envs, eBuildDir,
				CmakeBuilderImpl.PreparePathOptions.CREATE_IF_NOT_EXISTING);		
	}

	private CmakeBuilderImpl getCmakeBuilderImpl() {
		if (builderImpl == null) {
    		builderImpl = new CmakeBuilderImpl();
    	}
		return builderImpl;
	}

	private String checkCmake(String eProjectCmakePath, Node node, BuildListener listener, EnvVars envs) throws IOException,
			InterruptedException {
		String cmakeBin = CMAKE;
        String cmakePath = getDescriptor().cmakePath();
        if (cmakePath != null && cmakePath.length() > 0) {
    		cmakeBin = cmakePath;
    	}
        if (eProjectCmakePath != null && eProjectCmakePath.length() > 0) {
        	cmakeBin = eProjectCmakePath;
        }
        if (envs.containsKey(CMAKE_EXECUTABLE)) {
        	cmakeBin = envs.get(CMAKE_EXECUTABLE);
        }

        node.createLauncher(listener).launch().cmds(cmakeBin, "-version").stdout(listener).pwd(node.getRootPath()).join();

		return cmakeBin;
	}

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link CmakeBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>views/hudson/plugins/hello_world/CmakeBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private String cmakePath;
        private transient List<String> allowedBuildTypes;
        private transient String errorMessage;
        
        public DescriptorImpl() {
            super(CmakeBuilder.class);
            load();
            this.allowedBuildTypes = new ArrayList<String>();            
            this.allowedBuildTypes.add("Debug");
            this.allowedBuildTypes.add("Release");
            this.allowedBuildTypes.add("RelWithDebInfo");
            this.allowedBuildTypes.add("MinSizeRel");
            this.errorMessage = "Must be one of Debug, Release, RelWithDebInfo, MinSizeRel";
        }
        
        public FormValidation doCheckSourceDir(@AncestorInPath AbstractProject project, @QueryParameter final String value) throws IOException, ServletException {
            FilePath ws = project.getSomeWorkspace();
            if(ws==null) return FormValidation.ok();
            return ws.validateRelativePath(value,true,false);
        }
        
        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         */
        public FormValidation doCheckBuildDir(@QueryParameter final String value) throws IOException, ServletException {
            if(value.length()==0)
                return FormValidation.error("Please set a build directory");
            if(value.length() < 1)
                return FormValidation.warning("Isn't the name too short?");

            File file = new File(value);
            if (file.isFile())
                return FormValidation.error("build dir is a file");

            //TODO add more checks
            return FormValidation.ok();
        }

        /**
         * Performs on-the-fly validation of the form field 'buildType'.
         *
         * @param value
         */
        public FormValidation doCheckBuildType(@QueryParameter final String value) throws IOException, ServletException {
            for (String allowed : DescriptorImpl.this.allowedBuildTypes)
                if (value.equals(allowed))
                    return FormValidation.ok();
            if (value.length() > 0)
                return FormValidation.error(DescriptorImpl.this.errorMessage);

            return FormValidation.ok();
        }

        /**
         * Performs on-the-fly validation of the form field 'makeCommand'.
         *
         * @param value
         */
        public FormValidation doCheckMakeCommand(@QueryParameter final String value) throws IOException, ServletException {
            if (value.length() == 0) {
            	return FormValidation.error("Please set make command");
            }
            return FormValidation.validateExecutable(value);
        }

        
        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "CMake Build";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject o) throws FormException {
            // to persist global configuration information,
            // set that to properties and call save().
            cmakePath = o.getString("cmakePath");
            save();
            return super.configure(req, o);
        }

        public String cmakePath() {
        	return cmakePath;
        }
        
        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        	return req.bindJSON(CmakeBuilder.class, formData);
        }
        
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
        	return FreeStyleProject.class.isAssignableFrom(jobType)
                        || MatrixProject.class.isAssignableFrom(jobType);
        }
    }
}

