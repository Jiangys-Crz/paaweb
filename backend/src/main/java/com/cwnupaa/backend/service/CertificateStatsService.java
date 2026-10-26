package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.dto.CertificateStatsRequestDTO;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.vo.CertificateDetailVO;
import com.cwnupaa.backend.vo.CertificateStatsVO;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Service
public class CertificateStatsService {

    @Autowired
    private CertificateVerifyMapper certificateVerifyMapper;
    @Autowired
    private CertificateGrantMapper certificateGrantMapper;
    @Autowired
    private CertificateTypeMapper certificateTypeMapper;
    @Autowired
    private CertificateLevelMapper certificateLevelMapper;
    @Autowired
    private CertificateMemberMapper certificateMemberMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MajorMapper majorMapper;
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private ContestCategoryMapper contestCategoryMapper;
    @Autowired
    private FileSystemMapper fileSystemMapper;
    @Autowired
    private CertificateGrantService certificateGrantService;
    @Autowired
    private CertificateIssueMapper certificateIssueMapper;
    @Autowired
    private CertificateIssueMemberMapper certificateIssueMemberMapper;
    @Autowired
    private FileStorageService fileStorageService;

    public CertificateStatsVO getStats(CertificateStatsRequestDTO request) {
        List<CertificateDetailVO> details = fetchAndFilterData(request);
        
        CertificateStatsVO stats = new CertificateStatsVO();
        stats.setList(details);
        
        // Calculate stats
        Set<String> contestTypes = new HashSet<>();
        Set<String> users = new HashSet<>(); // Use Name+ID to distinguish? Or just UserID.
        // For verify, we have userId. For grant, we have userId or userAccount.
        // But details VO aggregates names. 
        // Let's count from the details list carefully.
        // Actually "Total Users" usually means distinct students who won awards.
        // But details rows might be Teams.
        // Let's iterate details and parse studentIds.
        
        int first = 0, second = 0, third = 0, other = 0;
        
        for (CertificateDetailVO vo : details) {
            if (vo.getContestName() != null) contestTypes.add(vo.getContestName());
            
            if (vo.getStudentIds() != null) {
                String[] ids = vo.getStudentIds().split("、");
                users.addAll(Arrays.asList(ids));
            }
            
            String level = vo.getAwardLevel();
            if (level != null) {
                if (level.contains("一等") || level.contains("金")) first++;
                else if (level.contains("二等") || level.contains("银")) second++;
                else if (level.contains("三等") || level.contains("铜")) third++;
                else other++;
            } else {
                other++;
            }
        }
        
        stats.setTotalContestTypes(contestTypes.size());
        stats.setTotalUsers(users.size());
        stats.setTotalFirstPrize(first);
        stats.setTotalSecondPrize(second);
        stats.setTotalThirdPrize(third);
        stats.setTotalOtherPrize(other);
        stats.setTotalAwards(first + second + third + other);
        
        return stats;
    }

    public byte[] exportStats(CertificateStatsRequestDTO request) throws IOException {
        List<CertificateDetailVO> details = fetchAndFilterData(request);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 1. Generate Excel
            ZipEntry excelEntry = new ZipEntry("获奖名单.xlsx");
            zos.putNextEntry(excelEntry);
            writeExcel(details, zos);
            zos.closeEntry();
            
            // 2. Add Certificates
        for (CertificateDetailVO vo : details) {
            if (vo.getFileId() != null) {
                FileSystem fs = fileSystemMapper.selectById(vo.getFileId());
                if (fs != null) {
                    byte[] fileBytes = getFileBytes(fs);
                    if (fileBytes != null) {
                        String ext = getFileExtension(fs.getName());
                        String entryName = "获奖证书/" + vo.getExportFileName() + "." + ext;
                        ZipEntry certEntry = new ZipEntry(entryName);
                        zos.putNextEntry(certEntry);
                        zos.write(fileBytes);
                        zos.closeEntry();
                    }
                }
            } else if (vo.getRemark() != null && vo.getRemark().contains("GrantFile_")) {
                try {
                    // Extract grantId
                    String remark = vo.getRemark();
                    int idx = remark.indexOf("GrantFile_");
                    String idPart = remark.substring(idx + 10);
                    if (idPart.contains(")")) idPart = idPart.substring(0, idPart.indexOf(")"));
                    Long grantId = Long.parseLong(idPart);
                    // Auto-generate the PDF for the grant. We pass a mock admin user to bypass ownership check
                    UserProfileVO mockAdmin = new UserProfileVO();
                    mockAdmin.setRole("admin");
                    
                    byte[] pdfBytes = certificateGrantService.generatePdf(grantId, mockAdmin);
                    if (pdfBytes != null) {
                        String entryName = "获奖证书/" + vo.getExportFileName() + ".pdf";
                        ZipEntry certEntry = new ZipEntry(entryName);
                        zos.putNextEntry(certEntry);
                        zos.write(pdfBytes);
                        zos.closeEntry();
                    }
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(CertificateStatsService.class).warn("操作失败，请检查请求参数和服务配置");
                }
            }
        }
        }
        return baos.toByteArray();
    }
    
    // Helper to fetch data
    private List<CertificateDetailVO> fetchAndFilterData(CertificateStatsRequestDTO request) {
        List<CertificateDetailVO> result = new ArrayList<>();
        
        // 1. Fetch CertificateVerify (Approved)
        QueryWrapper<CertificateVerify> verifyQw = new QueryWrapper<>();
        verifyQw.eq("status", "approved");
        if (request.getTermId() != null) verifyQw.eq("term", request.getTermId());
        if (request.getContestType() != null) verifyQw.eq("category_id", request.getContestType());
        
        List<CertificateVerify> verifies = certificateVerifyMapper.selectList(verifyQw);
        
        // Pre-fetch related data
        Map<Long, Users> userMap = new HashMap<>();
        Map<Long, CertificateLevel> levelMap = new HashMap<>();
        Map<Long, Major> majorMap = new HashMap<>();
        Map<Long, School> schoolMap = new HashMap<>();
        
        // 2. Fetch CertificateGrant
        // For Grants, we filter by Term via CertificateType
        QueryWrapper<CertificateGrant> grantQw = new QueryWrapper<>();
        List<CertificateGrant> grants = certificateGrantMapper.selectList(grantQw);
        
        // Filter Grants by Term & Type if needed
        Map<Long, CertificateType> typeMap = new HashMap<>();
        List<CertificateType> allTypes = certificateTypeMapper.selectList(null);
        for (CertificateType t : allTypes) typeMap.put(t.getCertificateTypeId(), t);
        
        if (request.getTermId() != null) {
            grants = grants.stream().filter(g -> {
                CertificateType t = typeMap.get(g.getCertificateTypeId());
                return t != null && Objects.equals(t.getTerm(), request.getTermId());
            }).collect(Collectors.toList());
        }
        
        // If ContestType filter is present, Verify is already filtered.
        // Grants don't have categoryId directly. If strict, exclude them.
        // Or if ContestCategory maps to something?
        // User requirement: "筛选信息有赛事类型".
        // If I filter by ContestType, likely I should exclude Grants unless I can map them.
        // Since I can't map them easily, I will exclude Grants if ContestType is specified.
        if (request.getContestType() != null) {
            grants = Collections.emptyList();
        }

        // Collect User IDs to fetch
        Set<Long> userIds = new HashSet<>();
        Set<String> studentIdsForLookup = new HashSet<>();
        verifies.forEach(v -> userIds.add(v.getUserId()));
        grants.forEach(g -> {
            if (g.getUserId() != null) userIds.add(g.getUserId());
            if (StringUtils.hasText(g.getUserAccount())) studentIdsForLookup.add(g.getUserAccount());
        });

        Set<Long> grantIssueIds = grants.stream().map(CertificateGrant::getIssueId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CertificateIssue> issueMap = new HashMap<>();
        Map<Long, List<CertificateIssueMember>> issueMembersMap = new HashMap<>();
        if (!grantIssueIds.isEmpty()) {
            certificateIssueMapper.selectBatchIds(grantIssueIds).forEach(issue -> issueMap.put(issue.getIssueId(), issue));
            List<CertificateIssueMember> issueMembers = certificateIssueMemberMapper.selectList(
                    new QueryWrapper<CertificateIssueMember>().in("issue_id", grantIssueIds).orderByAsc("sort_order", "issue_member_id"));
            for (CertificateIssueMember member : issueMembers) {
                issueMembersMap.computeIfAbsent(member.getIssueId(), ignored -> new ArrayList<>()).add(member);
                if (member.getUserId() != null) userIds.add(member.getUserId());
                if (StringUtils.hasText(member.getStudentId())) studentIdsForLookup.add(member.getStudentId());
            }
        }

        // Fetch Members for Verifies
        List<Long> verifyIds = verifies.stream().map(CertificateVerify::getCertificateVerifyId).collect(Collectors.toList());
        Map<Long, List<CertificateMember>> membersMap = new HashMap<>();
        if (!verifyIds.isEmpty()) {
            List<CertificateMember> members = certificateMemberMapper.selectList(new QueryWrapper<CertificateMember>().in("certificate_verify_id", verifyIds));
            for(CertificateMember m : members) {
                membersMap.computeIfAbsent(m.getCertificateVerifyId(), k -> new ArrayList<>()).add(m);
                if(m.getUserId() != null) userIds.add(m.getUserId());
                if (StringUtils.hasText(m.getStudentId())) studentIdsForLookup.add(m.getStudentId());
            }
        }

        Map<String, Users> userByStudentId = new HashMap<>();
        if (!studentIdsForLookup.isEmpty()) {
            List<Users> matchedUsers = userMapper.selectList(new QueryWrapper<Users>().in("number_id", studentIdsForLookup));
            for (Users u : matchedUsers) {
                if (StringUtils.hasText(u.getNumberId())) userByStudentId.put(u.getNumberId(), u);
                if (u.getUserId() != null) userIds.add(u.getUserId());
            }
        }
        userIds.remove(null);
        
        if (!userIds.isEmpty()) {
            List<Users> us = userMapper.selectBatchIds(userIds);
            us.forEach(u -> userMap.put(u.getUserId(), u));
            
            Set<Long> majorIds = us.stream().map(Users::getMajorId).filter(Objects::nonNull).collect(Collectors.toSet());
            if(!majorIds.isEmpty()) {
                List<Major> ms = majorMapper.selectBatchIds(majorIds);
                ms.forEach(m -> majorMap.put(m.getMajorId(), m));
                
                Set<Long> schoolIds = ms.stream().map(Major::getSchoolId).filter(Objects::nonNull).collect(Collectors.toSet());
                if(!schoolIds.isEmpty()) {
                    List<School> ss = schoolMapper.selectBatchIds(schoolIds);
                    ss.forEach(s -> schoolMap.put(s.getSchoolId(), s));
                }
            }
        }
        
        // Fetch Levels
        List<CertificateLevel> levels = certificateLevelMapper.selectList(null);
        levels.forEach(l -> levelMap.put(l.getLevelId(), l));
        
        // Process Verifies
        for (CertificateVerify v : verifies) {
            List<CertificateMember> mems = membersMap.getOrDefault(v.getCertificateVerifyId(), Collections.emptyList());
            if (!checkVerifyFilters(v, mems, request, userMap, userByStudentId, majorMap, schoolMap)) continue;
            
            CertificateDetailVO vo = new CertificateDetailVO();
            vo.setContestName(v.getEventName());
            
            CertificateLevel l = levelMap.get(v.getLevelId());
            if (l != null) {
                // Filter by level
                if (request.getMinLevel() != null && !request.getMinLevel().isEmpty()) {
                    int reqWeight = getLevelWeight(request.getMinLevel());
                    int recWeight = getLevelWeight(l.getLevelType());
                    if (recWeight < reqWeight) continue;
                }
                vo.setContestLevel(l.getLevelType());
                vo.setAwardLevel(l.getRankType());
            } else {
                if (request.getMinLevel() != null && !request.getMinLevel().isEmpty()) continue;
            }
            
            vo.setAdvisor(v.getAdvisor());
            vo.setAwardDate(formatDate(v.getTime()));
            vo.setTeamOrIndividual(Integer.valueOf(1).equals(v.getIsTeam()) ? "团队" : "个人");
            vo.setRemark(v.getSubProjectName());
            vo.setFileId(v.getFileId());
            
            // Users
            List<String> names = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            Users u = userMap.get(v.getUserId());
            
            // Set School/College from Applicant
            if (u != null) {
                Major m = majorMap.get(u.getMajorId());
                if (m != null) {
                    School s = schoolMap.get(m.getSchoolId());
                    if (s != null) vo.setSchoolName(s.getSchoolName());
                }
            }
            
            if (Integer.valueOf(1).equals(v.getIsTeam())) {
                // Sort by sortOrder
                mems.sort(Comparator.comparingInt(m -> m.getSortOrder() != null ? m.getSortOrder() : 999));
                
                if (mems.isEmpty()) {
                    // Fallback to applicant
                    if (u != null) {
                        names.add(u.getRealName());
                        ids.add(u.getNumberId());
                    }
                } else {
                    for (CertificateMember m : mems) {
                        names.add(m.getName());
                        ids.add(m.getStudentId());
                    }
                }
            } else {
                if (u != null) {
                    names.add(u.getRealName());
                    ids.add(u.getNumberId());
                }
            }

            if (!StringUtils.hasText(vo.getSchoolName())) {
                for (CertificateMember member : mems) {
                    Users memberUser = resolveUser(member.getUserId(), member.getStudentId(), userMap, userByStudentId);
                    if (memberUser == null) continue;
                    Major major = majorMap.get(memberUser.getMajorId());
                    School school = major == null ? null : schoolMap.get(major.getSchoolId());
                    if (school != null) {
                        vo.setSchoolName(school.getSchoolName());
                        break;
                    }
                }
            }
            
            // Keyword Filter Check (on student names/ids)
            if (StringUtils.hasText(request.getKeyword())) {
                boolean match = false;
                for (String name : names) if (name.contains(request.getKeyword())) match = true;
                for (String id : ids) if (id.contains(request.getKeyword())) match = true;
                if (!match) continue;
            }
            
            vo.setStudentNames(String.join("、", names));
            vo.setStudentIds(String.join("、", ids));
            
            result.add(vo);
        }
        
        // Process Grants. Modern imports have one issue per certificate but one grant per recipient,
        // so they must be aggregated by issue_id to keep "one row = one certificate".
        Set<Long> processedIssueIds = new HashSet<>();
        for (CertificateGrant g : grants) {
            if (g.getIssueId() != null) {
                if (!processedIssueIds.add(g.getIssueId())) continue;
                CertificateIssue issue = issueMap.get(g.getIssueId());
                if (issue == null || !"active".equals(issue.getStatus())) continue;
                List<CertificateIssueMember> issueMembers = issueMembersMap.getOrDefault(issue.getIssueId(), Collections.emptyList());

                boolean hasStructuralFilter = hasStructuralFilters(request);
                if (hasStructuralFilter) {
                    boolean memberMatches = issueMembers.stream()
                            .map(member -> resolveUserId(member.getUserId(), member.getStudentId(), userByStudentId))
                            .anyMatch(userId -> checkUserFilters(userId, request, userMap, majorMap, schoolMap));
                    if (!memberMatches) continue;
                }

                List<String> names = issueMembers.stream().map(CertificateIssueMember::getMemberName).collect(Collectors.toList());
                List<String> studentIds = issueMembers.stream().map(CertificateIssueMember::getStudentId).collect(Collectors.toList());
                if (StringUtils.hasText(request.getKeyword())) {
                    boolean match = names.stream().anyMatch(name -> name != null && name.contains(request.getKeyword()))
                            || studentIds.stream().anyMatch(id -> id != null && id.contains(request.getKeyword()))
                            || (issue.getCertificateNo() != null && issue.getCertificateNo().contains(request.getKeyword()))
                            || (issue.getTeamName() != null && issue.getTeamName().contains(request.getKeyword()));
                    if (!match) continue;
                }

                CertificateDetailVO vo = new CertificateDetailVO();
                CertificateType type = typeMap.get(issue.getCertificateTypeId());
                if (type != null) {
                    vo.setContestName(type.getTypeName());
                    String contestLevel = StringUtils.hasText(type.getLevelScope()) ? type.getLevelScope() : issue.getLevelScope();
                    CertificateLevel level = levelMap.get(type.getTypeId());
                    if (!StringUtils.hasText(contestLevel) && level != null) contestLevel = level.getLevelType();
                    if (StringUtils.hasText(contestLevel)) {
                        if (request.getMinLevel() != null && !request.getMinLevel().isEmpty()
                                && getLevelWeight(contestLevel) < getLevelWeight(request.getMinLevel())) continue;
                        vo.setContestLevel(contestLevel);
                    } else if (request.getMinLevel() != null && !request.getMinLevel().isEmpty()) {
                        continue;
                    }
                }
                vo.setAwardLevel(issue.getAwardName());
                vo.setAwardDate(formatDate(type != null && type.getTime() != null ? type.getTime() : issue.getIssueDate()));
                vo.setTeamOrIndividual(Integer.valueOf(1).equals(issue.getCertificateKind()) ? "团队" : "个人");
                vo.setStudentNames(String.join("、", names));
                vo.setStudentIds(String.join("、", studentIds));
                vo.setCertificateNo(issue.getCertificateNo());
                vo.setRemark("GrantFile_" + g.getCertificateDownloadId());

                for (CertificateIssueMember member : issueMembers) {
                    Users memberUser = resolveUser(member.getUserId(), member.getStudentId(), userMap, userByStudentId);
                    if (memberUser == null) continue;
                    Major major = majorMap.get(memberUser.getMajorId());
                    School school = major == null ? null : schoolMap.get(major.getSchoolId());
                    if (school != null) { vo.setSchoolName(school.getSchoolName()); break; }
                }
                result.add(vo);
                continue;
            }

            // Check filters on Grant User
            // Grant user might be null if manually entered?
            Long uid = resolveUserId(g.getUserId(), g.getUserAccount(), userByStudentId);
            Users u = userMap.get(uid);
            
            if (uid != null) {
                if (!checkUserFilters(uid, request, userMap, majorMap, schoolMap)) continue;
            } else {
                // Unregistered user, can only filter by keyword if matches name/account
                // Cannot filter by school/major/etc.
                if (request.getUniversityId() != null || request.getSchoolId() != null || 
                    request.getMajorId() != null || request.getGrade() != null || 
                    request.getClassName() != null || request.getDegreeType() != null) {
                    continue; // Skip unregistered users if structural filters are applied
                }
            }
            
            // Keyword check
            String realName = u != null ? u.getRealName() : g.getRealName();
            String numId = u != null ? u.getNumberId() : g.getUserAccount();
            
            if (StringUtils.hasText(request.getKeyword())) {
                boolean match = false;
                if (realName != null && realName.contains(request.getKeyword())) match = true;
                if (numId != null && numId.contains(request.getKeyword())) match = true;
                if (!match) continue;
            }
            
            CertificateDetailVO vo = new CertificateDetailVO();
            CertificateType type = typeMap.get(g.getCertificateTypeId());
            if (type != null) {
                vo.setContestName(type.getTypeName());
                vo.setAwardDate(formatDate(type.getTime()));
                // Type ID -> Level ID
                CertificateLevel l = levelMap.get(type.getTypeId());
                String contestLevel = StringUtils.hasText(type.getLevelScope()) ? type.getLevelScope() : (l == null ? null : l.getLevelType());
                if (StringUtils.hasText(contestLevel)) {
                    // Filter by level
                    if (request.getMinLevel() != null && !request.getMinLevel().isEmpty()) {
                        int reqWeight = getLevelWeight(request.getMinLevel());
                        int recWeight = getLevelWeight(contestLevel);
                        if (recWeight < reqWeight) continue;
                    }
                    vo.setContestLevel(contestLevel);
                    vo.setAwardLevel(l == null ? "-" : l.getRankType());
                } else {
                    if (request.getMinLevel() != null && !request.getMinLevel().isEmpty()) continue;
                }
                // File for Grant?
                // Grants are generated on the fly usually, or stored?
                // The entity doesn't have fileId. 
                // Maybe URL is in CertificateType? `urlId`.
                // `CertificateType` has `templateFileName`.
                // If it's dynamic, we might not be able to export the PDF unless we regenerate it.
                // But requirement says "获奖证书文件夹就是获奖名单上的所有证书".
                // If `CertificateGrant` doesn't store the generated file, we can't export it easily without regeneration.
                // Assuming for now we skip file for Grants or use Type template (which is wrong).
                // Wait, `CertificateGrant` has `certificateDownloadId`.
                // Maybe there is a separate table for generated files?
                // Or maybe `CertificateGrant` IS the record.
                // Given the constraints, I will skip file export for Grants if fileId is missing.
            }
            
            vo.setAdvisor(""); // No advisor for grants usually
            
            // Read isTeam from CertificateType
            if (type != null && Integer.valueOf(1).equals(type.getIsTeam())) {
                vo.setTeamOrIndividual("团队");
            } else {
                vo.setTeamOrIndividual("个人");
            }
            
            // Default remark logic for Grants: usually no sub-project, but we use it for GrantFile ID
            String baseRemark = "";
            
            vo.setStudentNames(realName);
            vo.setStudentIds(numId);
            
            // Encode grant ID in remark to trigger PDF generation during export
            if (baseRemark.isEmpty()) {
                vo.setRemark("GrantFile_" + g.getCertificateDownloadId());
            } else {
                vo.setRemark(baseRemark + " (GrantFile_" + g.getCertificateDownloadId() + ")");
            }
            
            if (u != null) {
                Major m = majorMap.get(u.getMajorId());
                if (m != null) {
                    School s = schoolMap.get(m.getSchoolId());
                    if (s != null) vo.setSchoolName(s.getSchoolName());
                }
            }
            
            result.add(vo);
        }
        
        // Assign No. and File Names
        int no = 1;
        for (CertificateDetailVO vo : result) {
            vo.setNo(no);
            String advisor = vo.getAdvisor() == null ? "" : "+" + vo.getAdvisor();
            String fName = no + "+" + (vo.getContestName() == null ? "" : vo.getContestName()) + 
                           "+" + (vo.getAwardLevel() == null ? "" : vo.getAwardLevel()) + 
                           advisor;
            // Clean filename
            fName = fName.replaceAll("[\\\\/:*?\"<>|]", "_");
            vo.setExportFileName(fName);
            no++;
        }
        
        return result;
    }
    
    private boolean checkVerifyFilters(CertificateVerify verify,
                                       List<CertificateMember> members,
                                       CertificateStatsRequestDTO request,
                                       Map<Long, Users> userMap,
                                       Map<String, Users> userByStudentId,
                                       Map<Long, Major> majorMap,
                                       Map<Long, School> schoolMap) {
        if (!hasStructuralFilters(request)) return true;
        if (checkUserFilters(verify.getUserId(), request, userMap, majorMap, schoolMap)) return true;
        if (members == null || members.isEmpty()) return false;
        for (CertificateMember member : members) {
            Long uid = resolveUserId(member.getUserId(), member.getStudentId(), userByStudentId);
            if (checkUserFilters(uid, request, userMap, majorMap, schoolMap)) return true;
        }
        return false;
    }

    private boolean hasStructuralFilters(CertificateStatsRequestDTO request) {
        return request.getUniversityId() != null || request.getSchoolId() != null
                || request.getMajorId() != null || StringUtils.hasText(request.getGrade())
                || StringUtils.hasText(request.getClassName()) || StringUtils.hasText(request.getDegreeType());
    }

    private Long resolveUserId(Long snapshotUserId, String studentId, Map<String, Users> userByStudentId) {
        if (snapshotUserId != null) return snapshotUserId;
        if (!StringUtils.hasText(studentId)) return null;
        Users user = userByStudentId.get(studentId);
        return user == null ? null : user.getUserId();
    }

    private Users resolveUser(Long snapshotUserId, String studentId, Map<Long, Users> userMap, Map<String, Users> userByStudentId) {
        Long uid = resolveUserId(snapshotUserId, studentId, userByStudentId);
        if (uid != null) return userMap.get(uid);
        if (!StringUtils.hasText(studentId)) return null;
        return userByStudentId.get(studentId);
    }

    private boolean checkUserFilters(Long userId, CertificateStatsRequestDTO request, 
                                     Map<Long, Users> userMap, Map<Long, Major> majorMap, Map<Long, School> schoolMap) {
        if (userId == null) return false;
        Users u = userMap.get(userId);
        if (u == null) return false;
        
        if (StringUtils.hasText(request.getGrade()) && !request.getGrade().equals(u.getGradeYear())) return false;
        if (StringUtils.hasText(request.getClassName()) && !request.getClassName().equals(u.getClassName())) return false;
        if (StringUtils.hasText(request.getDegreeType()) && !request.getDegreeType().equals(u.getEduBg())) return false;
        
        Major m = majorMap.get(u.getMajorId());
        if (request.getMajorId() != null) {
            if (m == null || !m.getMajorId().equals(request.getMajorId())) return false;
        }
        
        if (request.getSchoolId() != null) { // "学院" -> School entity
            if (m == null) return false;
            if (!m.getSchoolId().equals(request.getSchoolId())) return false;
        }
        
        if (request.getUniversityId() != null) {
            if (m == null) return false;
            School s = schoolMap.get(m.getSchoolId());
            if (s == null || !s.getUniversityId().equals(request.getUniversityId())) return false;
        }
        
        return true;
    }
    
    private String formatDate(java.time.LocalDate date) {
        if (date == null) return "";
        return date.format(DateTimeFormatter.ofPattern("yyyy年MM月"));
    }
    
    private void writeExcel(List<CertificateDetailVO> list, ZipOutputStream zos) throws IOException {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Sheet1");
        
        String[] headers = {"序号", "学院", "竞赛名称", "竞赛级别", "获奖等级", "第一指导老师", "获奖学生", "获奖学生学号", "获奖年月", "团队或个人", "证书编号", "备注"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
        
        int rowNum = 1;
        for (CertificateDetailVO vo : list) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(vo.getNo());
            row.createCell(1).setCellValue(vo.getSchoolName());
            row.createCell(2).setCellValue(vo.getContestName());
            row.createCell(3).setCellValue(vo.getContestLevel());
            row.createCell(4).setCellValue(vo.getAwardLevel());
            row.createCell(5).setCellValue(vo.getAdvisor());
            row.createCell(6).setCellValue(vo.getStudentNames());
            row.createCell(7).setCellValue(vo.getStudentIds());
            row.createCell(8).setCellValue(vo.getAwardDate());
            row.createCell(9).setCellValue(vo.getTeamOrIndividual());
            row.createCell(10).setCellValue(vo.getCertificateNo());
            // Clean remark for Excel export display
            String displayRemark = vo.getRemark();
            if (displayRemark != null && displayRemark.contains("GrantFile_")) {
                displayRemark = displayRemark.replaceAll("\\(GrantFile_\\d+\\)", "").trim();
                displayRemark = displayRemark.replaceAll("GrantFile_\\d+", "").trim();
            }
            row.createCell(11).setCellValue(displayRemark);
        }
        
        // Write to ZIP but don't close stream, just write bytes
        // Wait, poi write(OutputStream) writes the whole workbook.
        // We need to write it to zos BUT zos expects entry first (done in caller)
        // AND we should NOT close zos inside write(OutputStream).
        wb.write(zos);
        wb.close();
    }
    
    // Placeholder for file reading. In real app, need to read from disk/OSS using FileSystem path.
    private byte[] getFileBytes(FileSystem fs) {
        if (fs == null || fs.getUrl() == null) return null;
        try {
            return fileStorageService.readBytes(fs);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CertificateStatsService.class).warn("操作失败，请检查请求参数和服务配置");
        }
        return null;
    }
    
    private int getLevelWeight(String levelType) {
        if (levelType == null) return -1;
        if (levelType.contains("国际")) return 5;
        if (levelType.contains("国家")) return 4;
        if (levelType.contains("省")) return 3;
        if (levelType.contains("市")) return 2;
        if (levelType.contains("校")) return 1;
        if (levelType.contains("院")) return 0;
        return -1;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) return "pdf";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) return fileName.substring(dot + 1);
        return "pdf";
    }
}
