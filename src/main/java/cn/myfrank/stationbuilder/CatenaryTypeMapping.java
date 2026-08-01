package cn.myfrank.stationbuilder;

public enum CatenaryTypeMapping {
   Auto(0),
   MSDCatenary(1),
   MSDElectric(2),
   MSDRigidCatenary(3),
   MSDRigidSoftCatenary(4);

   private final int value;

   CatenaryTypeMapping(int value) {
      this.value = value;
   }
   public CatenaryTypeMapping fromValue(int value) {
       return switch (value) {
           case 0 -> Auto;
           case 1 -> MSDCatenary;
           case 2 -> MSDElectric;
           case 3 -> MSDRigidCatenary;
           case 4 -> MSDRigidSoftCatenary;
           default -> throw new IllegalStateException("Unexpected value: " + value);
       };
   }
   public int getValue() {
      return value;
   }
   public String getName() {
      return switch(this) {
         case Auto -> "Auto";
         case MSDCatenary -> "msd_catenary";
         case MSDElectric -> "msd_electric";
         case MSDRigidCatenary -> "msd_rigid_catenary";
         case MSDRigidSoftCatenary -> "msd_rigid_soft_catenary";
      };
   }
}