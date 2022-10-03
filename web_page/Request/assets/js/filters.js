angular.module('bonitasoft.ui.extensions',['ngSanitize'])
 .filter('toLabel', [function () {
   return function toLabel(status) {
     return "<span class=\"label label-"+severity(status.name)+" text-uppercase\">"+status.name+"</span>";
   };
}]).filter('userName', [function () {
   return function toLabel(user) {
     return user ? `${user.firstName} ${user.lastName}` : undefined;
   };
}])

function severity(status){
    switch(status) {
     case "new": return "primary";
     case "closed": return "success";
     case "in progress": return "warning";
     default:
       return "default";
   }
}