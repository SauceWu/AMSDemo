package me.sauce.plugin

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.ide.common.internal.WaitableExecutor
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import groovy.io.FileType
import java.util.concurrent.Callable

/**
 * @author sauce
 * @since 2019/1/19
 */
class ClickTransform extends Transform {
    private WaitableExecutor waitableExecutor

    ClickTransform() {
        this.waitableExecutor = WaitableExecutor.useGlobalSharedThreadPool()
    }

    @Override
    String getName() {
        return "clickTransform"
    }


    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.PROJECT_ONLY
    }


    @Override
    boolean isIncremental() {
        return true
    }

    @Override
    boolean isCacheable() {
        return true
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)
        long startTime = System.currentTimeMillis()
        def outputProvider = transformInvocation.outputProvider
        def context = transformInvocation.context
        transformInvocation.inputs.each {
            TransformInput input ->
                input.directoryInputs.each {
                    DirectoryInput directoryInput ->
                        File dir = directoryInput.file
                        File dest = outputProvider.getContentLocation(directoryInput.getName(),
                                directoryInput.getContentTypes(), directoryInput.getScopes(),
                                Format.DIRECTORY)
                        FileUtils.forceMkdir(dest)
                        String srcDirPath = dir.absolutePath
                        String destDirPath = dest.absolutePath
                        FileUtils.copyDirectory(dir, dest)
                        dir.traverse(type: FileType.FILES, nameFilter: ~/.*\.class/) {
                            File inputFile ->
                                waitableExecutor.execute(new Callable<Object>() {
                                    @Override
                                    Object call() throws Exception {
                                        File modified = modifyClassFile(dir, inputFile, context.getTemporaryDir())
                                        if (modified != null) {
                                            File target = new File(inputFile.absolutePath.replace(srcDirPath, destDirPath))
                                            if (target.exists()) {
                                                target.delete()
                                            }
                                            FileUtils.copyFile(modified, target)
                                            modified.delete()
                                        }
                                        return null
                                    }
                                })
                        }

                }
        }
        waitableExecutor.waitForTasksWithQuickFail(true)
        println(" 此次编译共耗时:${System.currentTimeMillis() - startTime}毫秒")
    }

    private File modifyClassFile(File dir, File classFile, File tempDir) {
        File modified = null
        FileOutputStream outputStream = null
        try {
            String className = path2ClassName(classFile.absolutePath.replace(dir.absolutePath + File.separator, ""))
            if (needHook(className)) {
                byte[] sourceClassBytes = IOUtils.toByteArray(new FileInputStream(classFile))
                byte[] modifiedClassBytes = modifyClasses(sourceClassBytes)
                if (modifiedClassBytes) {
                    modified = new File(tempDir, className.replace('.', '') + '.class')
                    if (modified.exists()) {
                        modified.delete()
                    }
                    modified.createNewFile()
                    outputStream = new FileOutputStream(modified)
                    outputStream.write(modifiedClassBytes)

                }
            } else {
                return classFile
            }
        } catch (Exception e) {
            e.printStackTrace()
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close()
                }
            } catch (Exception e) {
                e.printStackTrace()
            }
        }
        return modified
    }

    private byte[] modifyClasses(byte[] srcByteCode) {
        try {
            return modifyClass(srcByteCode)
        } catch (UnsupportedOperationException e) {
            throw e
        } catch (Exception ex) {
            ex.printStackTrace()
            return srcByteCode
        }
    }

    private byte[] modifyClass(byte[] srcClass) throws IOException {
        try {
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS)
            ClassVisitor classVisitor = new ClickClassVisitor(classWriter)
            ClassReader cr = new ClassReader(srcClass)
            cr.accept(classVisitor, ClassReader.EXPAND_FRAMES)
            return classWriter.toByteArray()
        } catch (UnsupportedOperationException e) {
            throw e
        } catch (Exception ex) {
            ex.printStackTrace()
            return srcClass
        }
    }

    private static String path2ClassName(String pathName) {
        pathName.replace(File.separator, ".").replace(".class", "")
    }

    boolean needHook(String className) {
        if (!isAndroidGenerated(className)) {
            return true
        }
        return false
    }

    boolean isAndroidGenerated(String name) {
        return name.startsWith("android") ||
                name.contains('R$') ||
                name.contains('R2$') ||
                name.contains('R.class') ||
                name.contains('R2.class') ||
                name.contains('BuildConfig.class')
    }
}