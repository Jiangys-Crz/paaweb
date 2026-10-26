package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.dto.CertificateStatsRequestDTO;
import com.cwnupaa.backend.service.CertificateStatsService;
import com.cwnupaa.backend.vo.CertificateStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("/admin/certificate/stats")
public class CertificateStatsController {

    @Autowired
    private CertificateStatsService certificateStatsService;

    @PostMapping("/list")
    public Result<CertificateStatsVO> getStats(@RequestBody CertificateStatsRequestDTO request) {
        return Result.success(certificateStatsService.getStats(request));
    }

    @PostMapping("/export")
    public void exportStats(@RequestBody CertificateStatsRequestDTO request, HttpServletResponse response) throws IOException {
        byte[] data = certificateStatsService.exportStats(request);
        
        response.reset();
        response.setHeader("Content-Disposition", "attachment; filename=\"certificates_export.zip\"");
        response.addHeader("Content-Length", "" + data.length);
        response.setContentType("application/zip; charset=UTF-8");
        response.getOutputStream().write(data);
    }
}
