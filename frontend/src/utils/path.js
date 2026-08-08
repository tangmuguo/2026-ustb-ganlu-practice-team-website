const AllPATH = {
  testPath: "user/hello",
  loginPath: "user/login",
  teamsPath: "user/teams",
  studentsPath: "user/students",
  addTeamPath:"user/add_team",
  updateTeamPath:"user/update_team",
  addStudentPath:"user/add_student",
  deleteTeamPath:"user/delete_team",
  regPath: "user/reg",
  //课件
  uploadImagePath:"courseDetail/uploadImage",
  uploadMaterialPath:"courseDetail/uploadFile",
  uploadWholeMaterialPath:"courseDetail/material",
  deleteMaterialPath:"courseDetail/deleteCourse",
  findAllCoursePath:"courseDetail/all",
  findCourseListPath:"courseDetail/list",
  uploadChunk:"courseDetail/uploadChunk",
  mergeChunks:"courseDetail/mergeChunks",
  checkFileExistPath:"courseDetail/checkFileExist",
  getMaterialDetailPath:"courseDetail/getDetail",
  getAllCourseTypePath:"courseDetail/allCourse",

  // 团队风采统一接口
  uploadTeamImagePath:"team-content/images/stage",
  teamContentMinePath:"team-content/mine",
  teamContentMembersPath:"team-content/members",
  teamContentPhotosPath:"team-content/photos",
  teamContentLogsPath:"team-content/logs",
  teamContentHonorsPath:"team-content/honors",
  teamContentMediaPath:"team-content/media",
  teamContentDeletePath:(type, id) => `team-content/${type}/${id}/delete`,
  teamContentPublicPath:(teamId) => `team-content/public/${teamId}`,
  teamContentMediaDownloadPath:(mediaId) => `team-content/media/${mediaId}/download`,
  teamContentMediaOwnerDownloadPath:(mediaId) => `team-content/media/${mediaId}/owner-download`,
  adminTeamContentMediaDownloadPath:(mediaId) => `admin/team-content/media/${mediaId}/download`,
  adminTeamContentTeamsPath:"admin/team-content/teams",
  adminTeamContentPath:"admin/team-content",
  adminTeamContentPublishPath:(type, id) => `admin/team-content/${type}/${id}/publish`,
  adminTeamContentRejectPath:(type, id) => `admin/team-content/${type}/${id}/reject`,
  adminTeamContentArchivePath:(type, id) => `admin/team-content/${type}/${id}/archive`,
  //轮播图
  bannerListPath:"banner/list",
  bannerAddPath:"banner/add",
  bannerUpdatePath:"banner/update",
  bannerDeletePath:"banner/delete",
  bannerUpdateSortPath:"banner/updateSort",
  bannerUpdateStatusPath:"banner/updateStatus",
  bannerUpdateLinkPath:"banner/updateLink",

  //留言
  messageAddPath:"/message/add",
  messageListPath:"/message/list",
  messageDeletePath:"/message/deleteMessage",
  replyAddPath:"/message/addReply",
  replyDeletePath:"/message/deleteReply",

  //新闻
  newsAddPath:"news/add",
  newsUpdatedPath:"news/update",
  newsDeletePath:"news/delete",
  newsGetPath:"news/get",
  newsListPath:"news/list",
  newsLimitPath:"news/limit"
}

export default AllPATH;
