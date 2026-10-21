package com.ancientkira.mca.client.model;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.client.render.VillagerRenderState;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;

/** Optional CPA/Player Animation Library bridge; no animation assets are bundled. */
public final class CpaVillagerAnimations {
    private static Api api;
    private static boolean checked;
    private static boolean failed;
    private static final Map<UUID, Runner> RUNNERS = new LinkedHashMap<>(128, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID,Runner> entry){return size()>128;}
    };
    private CpaVillagerAnimations() { }

    public static void apply(VillagerEntityBaseModelMCA model,VillagerRenderState state) {
        if(!Config.getInstance().usePlayerAnimationsForVillagers || state.mcaAnimationId==null
                || state.isBaby || state.isVillageDancing || state.isKidnapped || state.isCastingSpell
                || state.panicAnimationProgress>0 || state.isUsingItem || state.mcaSwinging) return;
        if(!checked){checked=true;try{api=new Api();}catch(ReflectiveOperationException|LinkageError absent){
            api=null;MCA.LOGGER.warn("CPA villager bridge initialization failed: {}",absent.toString());
        }}
        if(api==null || failed)return;
        try {
            String mode=mode(state);
            Settings settings=api.settings(mode);
            if(!settings.enabled())return;
            Object definition=api.animation(mode);
            if(definition==null)return;
            Runner runner=RUNNERS.get(state.mcaAnimationId);
            if(runner==null){runner=new Runner(api);RUNNERS.put(state.mcaAnimationId,runner);}
            api.speedField.setFloat(runner.speed,settings.speed());
            Object data=api.data.newInstance(state.ageInTicks-(int)state.ageInTicks,state.walkAnimationSpeed,false);
            if(runner.definition!=definition){api.trigger.invoke(runner.controller,definition);runner.definition=definition;}
            int tick=(int)state.ageInTicks;
            if(runner.lastTick!=tick){api.tick.invoke(runner.controller,data);runner.lastTick=tick;}
            api.setup.invoke(runner.controller,data);
            runner.part(api,model.head,"head");
            runner.part(api,model.body,"torso");
            runner.part(api,model.leftLeg,"left_leg");runner.part(api,model.rightLeg,"right_leg");
            // Keep MCA weapon aiming, shields, flowers and held-item poses authoritative.
            if(state.leftArmPose==net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY
                    && state.rightArmPose==net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY){
                runner.part(api,model.leftArm,"left_arm");runner.part(api,model.rightArm,"right_arm");
            }
            CommonVillagerModel.copyPartState(model.hat,model.head);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError error){
            failed=true;RUNNERS.clear();MCA.LOGGER.warn("CPA villager animation bridge unavailable; keeping MCA poses ({})",error.getClass().getSimpleName());
        }
    }

    static String mode(VillagerRenderState state){
        if(state.mcaSleeping)return "SLEEP_ANIMATION";
        if(state.isPassenger)return "MINECART_IDLE_ANIMATION";
        if(state.mcaClimbing)return state.walkAnimationSpeed>.05F?"CLIMB_ANIMATION":"CLIMB_IDLE_ANIMATION";
        if(state.isVisuallySwimming)return "IN_WATER_SWIM_ANIMATION";
        if(state.mcaInWater)return state.walkAnimationSpeed>.05F?"IN_WATER_FORWARD_ANIMATION":"IN_WATER_IDLE_ANIMATION";
        if(state.mcaFalling)return "FALL_ANIMATION";
        if(state.walkAnimationSpeed>.05F)return state.isCrouching?"WALK_SNEAK_ANIMATION":state.mcaRunning?"RUN_ANIMATION":"WALK_ANIMATION";
        return state.isCrouching?"IDLE_SNEAK_ANIMATION":"IDLE_STANDING_ANIMATION";
    }

    private record Settings(boolean enabled,float speed) { }

    private static final class Runner {
        final Object controller,speed;Object definition;int lastTick=Integer.MIN_VALUE;
        final Map<String,Object> bones=new HashMap<>();
        Runner(Api a)throws ReflectiveOperationException{
            Object handler=Proxy.newProxyInstance(a.handler.getClassLoader(),new Class[]{a.handler},(p,m,args)->{
                if(m.getName().equals("handle"))return a.keepPlaying;
                if(m.getName().equals("toString"))return "MCA CPA animation handler";
                if(m.getName().equals("hashCode"))return System.identityHashCode(p);
                return p==args[0];
            });
            Function<Object,Object> runtime=c->{try{return a.engine.invoke(null,c);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}};
            controller=a.controller.newInstance(handler,runtime);
            speed=a.speed.newInstance(1.0F);a.addModifier.invoke(controller,speed);
        }
        void part(Api a,ModelPart part,String name)throws ReflectiveOperationException{
            Object bone=bones.get(name);if(bone==null){bone=a.bone.newInstance(name);bones.put(name,bone);}
            a.copyPart.invoke(null, part, bone);
            a.transform.invoke(controller,bone);
            Vector3f pos=(Vector3f)a.position.get(bone),rot=(Vector3f)a.rotation.get(bone),size=(Vector3f)a.scale.get(bone);
            if(!pos.isFinite()||!rot.isFinite()||!size.isFinite())return;
            a.applyPart.invoke(null, part, bone, part.getInitialPose());
        }
    }
    private static final class Api {
        final Class<?> handler;Class<?> ids;final Object keepPlaying;
        final Constructor<?> controller,data,bone,speed;
        final Method getAnimation,id,engine,trigger,tick,setup,transform,addModifier,copyPart,applyPart;
        final Field position,rotation,scale,speedField;
        final Map<String,Field[]> configPaths=new HashMap<>();
        Field configField;Method mainConfig,generalConfig;
        Api()throws ReflectiveOperationException{
            String core="com.zigythebird.playeranimcore.";
            Class<?> control=Class.forName(core+"animation.AnimationController");
            Class<?> animation=Class.forName(core+"animation.Animation");
            Class<?> dataType=Class.forName(core+"animation.AnimationData");
            Class<?> boneType=Class.forName(core+"bones.PlayerAnimBone");
            handler=Class.forName(core+"animation.AnimationController$AnimationStateHandler");
            controller=Class.forName(core+"animation.HumanoidAnimationController").getConstructor(handler,Function.class);
            data=dataType.getConstructor(float.class,float.class,boolean.class);bone=boneType.getConstructor(String.class);
            engine=Class.forName(core+"molang.MolangLoader").getMethod("createNewEngine",control);
            keepPlaying=Class.forName(core+"enums.PlayState").getField("CONTINUE").get(null);
            try{ids=Class.forName("com.github.razorplay01.cpa.util.enums.AnimationsId");}
            catch(ClassNotFoundException older){ids=Class.forName("com.github.razorplay01.cpa.platform.common.util.enums.AnimationsId");}
            id=ids.getMethod("getAnimationId");
            getAnimation=Class.forName("com.github.razorplay01.cpa.ModTemplate").getMethod("getAnimation",String.class);
            trigger=control.getMethod("triggerAnimation",animation);tick=control.getMethod("tick",dataType);setup=control.getMethod("setupAnim",dataType);
            transform=control.getMethod("get3DTransform",boneType);
            Class<?> speedType=Class.forName(core+"animation.layered.modifier.SpeedModifier");
            speed=speedType.getConstructor(float.class);speedField=speedType.getField("speed");
            addModifier=control.getMethod("addModifierLast",Class.forName(core+"animation.layered.modifier.AbstractModifier"));
            try {
                configField=Class.forName("com.github.razorplay01.cpa.ModTemplate").getField("CONFIG");
                mainConfig=configField.getType().getMethod("getMainAnimations");generalConfig=configField.getType().getMethod("getGeneral");
                Map<String,String> paths=Map.of("WALK_ANIMATION","moveAnimations.walkingAnimationConfig", "RUN_ANIMATION","moveAnimations.runningAnimationConfig",
                        "WALK_SNEAK_ANIMATION","moveAnimations.walkingSneakAnimationConfig", "IDLE_STANDING_ANIMATION","idleAnimations.idleStandingAnimationConfig", "IDLE_SNEAK_ANIMATION","idleAnimations.idleSneakAnimationConfig");
                for(var entry:paths.entrySet()) {
                    String[] names=entry.getValue().split("\\.");Field first=mainConfig.getReturnType().getField(names[0]);
                    configPaths.put(entry.getKey(),new Field[]{first,first.getType().getField(names[1])});
                }
            }catch(NoSuchFieldException|NoSuchMethodException unsupportedConfig){configField=null;configPaths.clear();}
            Class<?> renderUtil=Class.forName("com.zigythebird.playeranim.util.RenderUtil");
            copyPart=renderUtil.getMethod("copyVanillaPart",ModelPart.class,boneType);
            applyPart=renderUtil.getMethod("translatePartToBone",ModelPart.class,boneType,net.minecraft.client.model.geom.PartPose.class);
            position=boneType.getField("position");rotation=boneType.getField("rotation");scale=boneType.getField("scale");
        }
        Settings settings(String mode)throws ReflectiveOperationException {
            if(configField==null || configField.get(null)==null)return new Settings(true,1);
            Object config=configField.get(null),general=generalConfig.invoke(config);
            float speed=((Number)general.getClass().getMethod("getAnimationSpeedMultiplier").invoke(general)).floatValue();
            Field[] path=configPaths.get(mode);
            if(path!=null){
                Object setting=path[1].get(path[0].get(mainConfig.invoke(config)));
                if(!(boolean)setting.getClass().getMethod("isEnabled").invoke(setting))return new Settings(false,1);
                speed*=((Number)setting.getClass().getMethod("getSpeedMultiplier").invoke(setting)).floatValue();
            }
            return new Settings(true,Float.isFinite(speed)?Math.clamp(speed,0.01F,10.0F):1);
        }
        Object animation(String mode)throws ReflectiveOperationException{
            try{return getAnimation.invoke(null,id.invoke(ids.getField(mode).get(null)));}
            catch(NoSuchFieldException missing){return null;}
        }
    }
}
