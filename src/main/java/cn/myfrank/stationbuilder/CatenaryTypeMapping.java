package cn.myfrank.stationbuilder;

public enum CatenaryTypeMapping {
   MinecraftBlock(0),
   MSDCatenary(1),
   MSDElectric(2),
   MSDRigidCatenary(3),
   MSDRigidSoftCatenary(4);

   private final int value;

   CatenaryTypeMapping(int value) {
      this.value = value;
   }
   public CatenaryTypeMapping fromValue(int value) {
      switch(value) {
         case 1: return MSDCatenary;
         case 2: return MSDElectric;
         case 3: return MSDRigidCatenary;
         case 4: return MSDRigidSoftCatenary;
         default: return MinecraftBlock;
      }
   }
   public int getValue() {
      return value;
   }
   public String getName() {
      return switch(this) {
         case MinecraftBlock -> "minecraft_block";
         case MSDCatenary -> "msd_catenary";
         case MSDElectric -> "msd_electric";
         case MSDRigidCatenary -> "msd_rigid_catenary";
         case MSDRigidSoftCatenary -> "msd_rigid_soft_catenary";
      };
   }
}